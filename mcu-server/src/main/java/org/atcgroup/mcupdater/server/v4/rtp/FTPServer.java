package org.atcgroup.mcupdater.server.v4.rtp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.*;
import org.atcgroup.mcupdater.server.v4.storage.CASResourceStorage;
import org.atcgroup.mcupdater.server.v4.HttpResourceServer;
import org.atcgroup.mcupdater.util.FileDigest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server-side resource resolution and sender. Filesystem access is isolated from Netty EventLoops.
 */
public final class FTPServer implements AutoCloseable {
    private final CASResourceStorage storage;
    private volatile P61_RTPProtocolInfo protocolInfo;
    private volatile HttpResourceServer httpServer;
    private boolean ownsHttpServer;
    private final Map<Channel, Batch> batches = new HashMap<>();
    private final ThreadPoolExecutor disk;
    private final java.util.concurrent.ScheduledExecutorService timer;
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private volatile boolean closed;


    private static final class OfferedFile {
        final P63_RTPBatchResponse.File metadata;
        final java.io.File source;
        final String readLock;
        final FileChannel channel;
        boolean inlineRequested;

        OfferedFile(P63_RTPBatchResponse.File metadata, java.io.File source, String readLock, FileChannel channel) {
            this.metadata = metadata;
            this.source = source;
            this.readLock = readLock;
            this.channel = channel;
        }
    }

    private static final class Batch {
        final String id;
        final Map<String, OfferedFile> files = new LinkedHashMap<>();
        long lastActivity = System.nanoTime();
        boolean releasing;

        Batch(String id) {
            this.id = id;
        }
    }

    public FTPServer(CASResourceStorage storage) {
        this(storage, new P61_RTPProtocolInfo(1, TransferFileReceiver.FILE_MTU, false, ""));
    }

    public FTPServer(CASResourceStorage storage, P61_RTPProtocolInfo protocolInfo) {
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
        this.protocolInfo = java.util.Objects.requireNonNull(protocolInfo, "protocolInfo");
        // Serial sender IO keeps manifest preparation, READY, reads and cleanup ordered across channels.
        this.disk = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), runnable -> new Thread(runnable, "resource-server-disk"), new ThreadPoolExecutor.AbortPolicy()) {
            @Override
            protected void terminated() {
                termination.complete(null);
            }
        };
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "resource-server-timeout"));
        this.timer.scheduleWithFixedDelay(this::scheduleCleanup, 1, 1, TimeUnit.SECONDS);
    }

    /** Attach a running HTTP server. Its lifecycle remains with the caller. */
    public FTPServer(CASResourceStorage storage, HttpResourceServer httpServer, String publicEndpoint) {
        this(storage, httpCapabilities(storage, httpServer, publicEndpoint));
        this.httpServer = httpServer;
    }

    private static P61_RTPProtocolInfo httpCapabilities(CASResourceStorage storage, HttpResourceServer server, String endpoint) {
        if (server.getStorage() != storage) throw new IllegalArgumentException("HTTP and RTP must share CAS storage");
        if (!server.isRunning()) throw new IllegalStateException("HTTP server is not running");
        return new P61_RTPProtocolInfo(1, TransferFileReceiver.FILE_MTU, true, endpoint);
    }

    /** Start an owned HTTP server on an application thread, before clients initialize RTP. Port 0 is allowed. */
    public synchronized void startHttp(int port, String advertisedHost) throws InterruptedException {
        if (this.closed || this.httpServer != null) throw new IllegalStateException("Server closed or HTTP already configured");
        var http = new HttpResourceServer(this.storage);
        try {
            http.start(port);
            var endpoint = new java.net.URI("http", null, advertisedHost, http.getLocalAddress().getPort(), "/", null, null);
            this.protocolInfo = httpCapabilities(this.storage, http, endpoint.toString());
            this.httpServer = http;
            this.ownsHttpServer = true;
        } catch (java.net.URISyntaxException error) {
            http.close();
            throw new IllegalArgumentException("Invalid advertised HTTP host", error);
        } catch (InterruptedException | RuntimeException error) {
            http.close();
            throw error;
        }
    }

    private P61_RTPProtocolInfo capabilities() {
        var info = this.protocolInfo;
        var http = this.httpServer;
        return http != null && !http.isRunning()
                ? new P61_RTPProtocolInfo(info.getMaxWorker(), info.getTransportMTU(), false, "") : info;
    }

    /**
     * Both handlers are added after the external pipeline's SimpNet codec.
     */
    public PacketInboundHandler masterHandler() {
        return new MasterConnectionHandler();
    }

    /**
     * The external connection layer authenticates and associates this Worker with its Master.
     */
    public PacketInboundHandler workerHandler(Channel masterChannel) {
        return new WorkerHandler(java.util.Objects.requireNonNull(masterChannel, "masterChannel"));
    }

    private void submit(Channel channel, Runnable action) {
        if (this.closed) return;
        try {
            this.disk.execute(() -> {
                try {
                    action.run();
                } catch (Exception error) {
                    channel.close();
                }
            });
        } catch (RejectedExecutionException error) {
            channel.close();
        }
    }

    private final class MasterConnectionHandler extends PacketInboundHandler {
        private boolean initialized;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
            if (packet instanceof P60_RTPProtocolInit) {
                if (closed) {
                    ctx.close();
                    return;
                }
                this.initialized = true;
                ctx.writeAndFlush(capabilities());
            } else if ((packet instanceof P62_RTPBatchRequest || packet instanceof P64_RTPBatchRelease) && !this.initialized) {
                ctx.close();
            } else if (packet instanceof P62_RTPBatchRequest request) {
                submit(ctx.channel(), () -> {
                    if (!ctx.channel().isActive()) return;
                    try {
                        prepare(ctx.channel(), request);
                    } catch (Exception error) {
                        ctx.close();
                    }
                });
            } else if (packet instanceof P64_RTPBatchRelease release) {
                submit(ctx.channel(), () -> {
                    var batch = batches.get(ctx.channel());
                    if (batch == null) return;
                    if (!batch.id.equals(release.getBatchId())) {
                        ctx.close();
                        return;
                    }
                    batch.releasing = true;
                    cleanup(ctx.channel(), batch);
                });
            } else {
                ctx.fireChannelRead(packet);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            scheduleCleanup();
            ctx.fireChannelInactive();
        }
    }

    private final class WorkerHandler extends PacketInboundHandler {
        private final Channel master;

        private WorkerHandler(Channel master) {
            this.master = master;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
            if (!(packet instanceof P71_FTPInlineRequest || packet instanceof P70_FTPRangeRequest || packet instanceof P75_FTPStatusResult || packet instanceof P73_FTPCancel)) {
                ctx.fireChannelRead(packet);
                return;
            }
            submit(ctx.channel(), () -> {
                try {
                    handleWorker(this.master, ctx.channel(), packet);
                } catch (IOException error) {
                    ctx.close();
                }
            });
        }
    }

    private void handleWorker(Channel master, Channel channel, Packet packet) throws IOException {
        if (this.closed || !channel.isActive() || !master.isActive()) return;
        if (packet instanceof P75_FTPStatusResult || packet instanceof P73_FTPCancel) return;
        var batch = this.batches.get(master);
        if (batch == null || batch.releasing) throw new IOException("No active resource batch");
        batch.lastActivity = System.nanoTime();
        if (packet instanceof P71_FTPInlineRequest ready) {
            var file = requireFile(batch, ready.getTaskId());
            if (file.inlineRequested || file.metadata.size() > TransferFileReceiver.INLINE_LIMIT) {
                throw new IOException("Inline request is invalid for this task");
            }
            file.inlineRequested = true;
            sendPart(channel, file, 0);
        } else if (packet instanceof P70_FTPRangeRequest request) {
            var file = requireFile(batch, request.getTaskId());
            if (file.metadata.size() <= TransferFileReceiver.INLINE_LIMIT) {
                throw new IOException("Range request is invalid for an inline task");
            }
            sendPart(channel, file, request.getPartId());
        }
    }

    private void prepare(Channel channel, P62_RTPBatchRequest request) throws Exception {
        if (this.batches.containsKey(channel)) throw new IOException("Batch already active");
        var batch = new Batch(request.getBatchId());
        this.batches.put(channel, batch);
        var missing = new ArrayList<String>();
        try {
            for (var id : request.getResourceIds()) {
                var source = this.storage.getResource(id);
                if (source.isEmpty()) {
                    missing.add(id);
                    continue;
                }
                var file = source.get();
                var taskId = UUID.randomUUID().toString();
                FileChannel input = null;
                boolean offered = false;
                try {
                    try {
                        input = FileChannel.open(file.toPath(), StandardOpenOption.READ);
                    } catch (NoSuchFileException disappeared) {
                        missing.add(id);
                        continue;
                    }
                    var sha1 = HexFormat.of().formatHex(FileDigest.calculateSHA1(file));
                    if (!id.equals(sha1)) throw new IOException("CAS resource SHA-1 mismatch: " + id);
                    var metadata = new P63_RTPBatchResponse.File(id, taskId, input.size(), sha1);
                    batch.files.put(taskId, new OfferedFile(metadata, file, taskId, input));
                    offered = true;
                } finally {
                    if (!offered) {
                        if (input != null) input.close();
                    }
                }
            }
            batch.lastActivity = System.nanoTime();
            if (!channel.isActive() || this.closed) {
                batch.releasing = true;
                cleanup(channel, batch);
                return;
            }
            channel.writeAndFlush(new P63_RTPBatchResponse(batch.id, batch.files.values()
                                                                                .stream()
                                                                                .map(file -> file.metadata)
                                                                                .toList(), missing, false))
                   .addListener(sent -> {
                       if (!sent.isSuccess()) channel.close();
                   });
        } catch (Exception error) {
            batch.releasing = true;
            cleanup(channel, batch);
            // Operational failures are not reported as "missing" and never produce a partial promise.
            channel.writeAndFlush(new P63_RTPBatchResponse(batch.id, java.util.List.of(), java.util.List.of(), true));
        }
    }

    private static OfferedFile requireFile(Batch batch, String taskId) throws IOException {
        var file = batch.files.get(taskId);
        if (file == null) throw new IOException("Task does not belong to this connection");
        return file;
    }

    private void sendPart(Channel channel, OfferedFile file, int partId) throws IOException {
        long start = (long) partId * TransferFileReceiver.FILE_MTU;
        if (partId < 0 || (start >= file.metadata.size() && !(partId == 0 && file.metadata.size() == 0))) {
            throw new IOException("Part outside file");
        }
        byte[] data = new byte[(int) Math.min(TransferFileReceiver.FILE_MTU, file.metadata.size() - start)];
        var buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            int read = file.channel.read(buffer, start + buffer.position());
            if (read <= 0) throw new IOException("CAS resource read made no progress");
        }
        channel.writeAndFlush(new P72_FTPPayload(file.metadata.taskId(), partId, data)).addListener(sent -> {
            if (!sent.isSuccess()) channel.close();
        });
    }

    private void cleanup(Channel channel, Batch batch) {
        var iterator = batch.files.values().iterator();
        while (iterator.hasNext()) {
            var file = iterator.next();
            try {
                file.channel.close();
                iterator.remove();
            } catch (IOException | RuntimeException ignored) {
                // Retry resource release from the periodic cleanup.
            }
        }
        if (batch.files.isEmpty()) this.batches.remove(channel, batch);
    }

    private void scheduleCleanup() {
        if (this.disk.isShutdown()) return;
        try {
            this.disk.execute(() -> {
                for (var entry : java.util.List.copyOf(this.batches.entrySet())) {
                    var batch = entry.getValue();
                    if (this.closed || !entry.getKey()
                                             .isActive() || batch.releasing || System.nanoTime() - batch.lastActivity > Duration.ofMinutes(2)
                                                                                                                                .toNanos()) {
                        batch.releasing = true;
                        cleanup(entry.getKey(), batch);
                    }
                }
                if (this.closed && this.batches.isEmpty()) {
                    if (this.ownsHttpServer) this.httpServer.close();
                    this.timer.shutdown();
                    this.disk.shutdown();
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Timer retries when the bounded disk queue has room.
        }
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        scheduleCleanup();
    }

    public java.util.concurrent.CompletionStage<Void> termination() {
        return this.termination.minimalCompletionStage();
    }
}
