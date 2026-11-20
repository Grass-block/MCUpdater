package org.atcgroup.mcupdater.server.v4.rtp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import me.gb2022.commons.file.FilePath;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P60_RTPProtocolInit;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P61_RTPProtocolInfo;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P72_FTPPayload;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P63_RTPBatchResponse;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

public final class FTPClient implements AutoCloseable {
    private final MasterConnectionHandler masterHandler = new MasterConnectionHandler();
    private final NioEventLoopGroup workers = new NioEventLoopGroup(1);
    private final DefaultChannelGroup workerChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final FilePath workingDirectory;
    private final TransferManager transferManager;
    private final boolean ownsManager;
    private final Map<String, ResourceTransferBatch> batches = new HashMap<>();
    private boolean closed;
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    public FTPClient(FilePath workingDirectory) {
        this(workingDirectory, new TransferManager(), true);
    }

    public FTPClient(FilePath workingDirectory, TransferManager transferManager) {
        this(workingDirectory, transferManager, false);
    }


    private CompletableFuture<P61_RTPProtocolInfo> initialization;
    private java.util.concurrent.ScheduledFuture<?> initTimeout;

    /** Negotiate capabilities once per Master connection; prefer advertised HTTP when available. */
    public synchronized CompletionStage<P61_RTPProtocolInfo> runInit() {
        if (this.closed) throw new IllegalStateException("FTP client is closed");
        var channel = this.masterHandler.channel;
        if (channel == null || !channel.isActive()) throw new IllegalStateException("No active resource connection");
        if (this.initialization != null) return this.initialization.minimalCompletionStage();
        this.initialization = new CompletableFuture<>();
        var pending = this.initialization;
        this.initTimeout = channel.eventLoop().schedule(() -> {
            synchronized (FTPClient.this) {
                if (pending.completeExceptionally(new java.util.concurrent.TimeoutException("RTP initialization timed out"))) {
                    channel.close();
                }
            }
        }, 10, java.util.concurrent.TimeUnit.SECONDS);
        var timeout = this.initTimeout;
        channel.writeAndFlush(new P60_RTPProtocolInit()).addListener(sent -> {
            if (!sent.isSuccess()) {
                synchronized (FTPClient.this) {
                    pending.completeExceptionally(sent.cause());
                    timeout.cancel(false);
                }
                channel.close();
            }
        });
        return pending.minimalCompletionStage();
    }

    /** Returns immediately. Call get() on an application thread to wait for the entire batch. */
    public synchronized CompletableFuture<TransferBatchResult> requestFiles(String[] resourceIds, Consumer<TransferProgress> progress) {
        if (this.closed) {
            throw new IllegalStateException("FTP client is closed");
        }
        var channel = this.masterHandler.channel;
        if (channel == null || !channel.isActive()) throw new IllegalStateException("No active resource connection");
        if (this.initialization == null || !this.initialization.isDone() || this.initialization.isCompletedExceptionally()) {
            throw new IllegalStateException("RTP initialization has not completed");
        }
        if (!this.batches.isEmpty()) throw new IllegalStateException("A resource batch is already active");
        var batch = new ResourceTransferBatch(this.transferManager, channel, this.workingDirectory.file().toPath(),
                resourceIds, progress, () -> connectWorker(channel),
                this.initialization.join().supportsHTTP() ? this.initialization.join().getHttpEndpoint() : null);
        this.batches.put(batch.getBatchId(), batch);
        var future = batch.start();
        batch.termination().whenComplete((result, error) -> {
            synchronized (FTPClient.this) {
                this.batches.remove(batch.getBatchId(), batch);
            }
        });
        return future;
    }

    private FTPClient(FilePath workingDirectory, TransferManager transferManager, boolean ownsManager) {
        this.workingDirectory = workingDirectory;
        this.transferManager = java.util.Objects.requireNonNull(transferManager, "transferManager");
        this.ownsManager = ownsManager;
    }

    /** Add after the external connection's SimpNet codec. The caller owns the Master Channel. */
    public PacketInboundHandler masterHandler() {
        return this.masterHandler;
    }

    private synchronized CompletableFuture<Channel> connectWorker(Channel master) {
        var result = new CompletableFuture<Channel>();
        if (this.closed || !master.isActive()) {
            result.completeExceptionally(new IllegalStateException("Master connection closed"));
            return result;
        }
        new Bootstrap().group(this.workers).channel(NioSocketChannel.class)
                .handler(new TransferChannelInitializer(this.transferManager))
                .connect(master.remoteAddress()).addListener(connected -> {
                    var channel = ((io.netty.channel.ChannelFuture) connected).channel();
                    this.workerChannels.add(channel);
                    synchronized (FTPClient.this) {
                        if (!connected.isSuccess() || this.closed || !master.isActive()) {
                            channel.close();
                            result.completeExceptionally(connected.cause() != null ? connected.cause()
                                    : new IllegalStateException("Master connection closed"));
                        } else {
                            result.complete(channel);
                        }
                    }
                });
        return result;
    }

    public CompletionStage<Void> acceptChunk(P72_FTPPayload part) {
        return this.transferManager.receive(part);
    }

    public TransferManager getTransferManager() {
        return this.transferManager;
    }

    public CompletionStage<Void> requestRanges(String taskId, Channel channel, int window) {
        return this.transferManager.requestRanges(taskId, channel, window);
    }

    public FilePath getWorkingDirectory() {
        return this.workingDirectory;
    }

    @Override
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;
        if (this.initTimeout != null) this.initTimeout.cancel(false);
        if (this.initialization != null) this.initialization.completeExceptionally(new java.util.concurrent.CancellationException("FTP client closed"));
        var pendingBatches = java.util.List.copyOf(this.batches.values());
        for (var batch : pendingBatches) {
            batch.disconnected(new java.util.concurrent.CancellationException("FTP client closed"));
        }
        this.workerChannels.close();
        var workerTermination = new CompletableFuture<Void>();
        this.workers.shutdownGracefully().addListener(done -> {
            if (done.isSuccess()) workerTermination.complete(null);
            else workerTermination.completeExceptionally(done.cause());
        });
        if (this.ownsManager) {
            this.transferManager.close();
        }
        var pending = new java.util.ArrayList<CompletableFuture<?>>();
        pending.add(workerTermination);
        pendingBatches.forEach(batch -> pending.add(batch.termination().toCompletableFuture()));
        if (this.ownsManager) pending.add(this.transferManager.termination().toCompletableFuture());
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            if (error == null) this.termination.complete(null);
            else this.termination.completeExceptionally(error);
        });
    }

    /** Completes after close, including active HTTP staging cleanup and owned resources. */
    public CompletionStage<Void> termination() { return this.termination.minimalCompletionStage(); }

    private final class MasterConnectionHandler extends PacketInboundHandler {
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            if (ctx.channel().isActive()) this.channel = ctx.channel();
        }
        private volatile Channel channel;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            this.channel = ctx.channel();
            ctx.fireChannelActive();
        }

        public Channel getChannel() {
            return channel;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
            if (packet instanceof P61_RTPProtocolInfo info) {
                synchronized (FTPClient.this) {
                    if (initialization == null || initialization.isDone()) return;
                    initTimeout.cancel(false);
                    if (!info.supportsHTTP() && info.getTransportMTU() != TransferFileReceiver.FILE_MTU) {
                        initialization.completeExceptionally(new IllegalArgumentException("Unsupported B-FTP MTU"));
                        ctx.close();
                    } else {
                        initialization.complete(info);
                    }
                }
            } else if (packet instanceof P63_RTPBatchResponse manifest) {
                ResourceTransferBatch batch;
                synchronized (FTPClient.this) {
                    batch = batches.get(manifest.getBatchId());
                }
                if (batch != null) batch.manifest(manifest);
            } else {
                ctx.fireChannelRead(packet);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            synchronized (FTPClient.this) {
                this.channel = null;
                if (initTimeout != null) initTimeout.cancel(false);
                if (initialization != null) initialization.completeExceptionally(new java.io.IOException("Resource connection closed"));
                initialization = null;
                for (var batch : java.util.List.copyOf(batches.values())) {
                    batch.disconnected(new java.io.IOException("Resource connection closed"));
                }
            }
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            synchronized (FTPClient.this) {
                if (initialization != null) initialization.completeExceptionally(cause);
            }
            ctx.close();
        }
    }
}
