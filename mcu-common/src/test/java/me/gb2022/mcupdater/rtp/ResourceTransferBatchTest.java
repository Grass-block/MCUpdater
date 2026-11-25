package me.gb2022.mcupdater.rtp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.rtp.protocol.P61_RTPProtocolInfo;
import me.gb2022.mcupdater.rtp.protocol.RTPProtocol;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static me.gb2022.mcupdater.rtp.TransferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceTransferBatchTest {
    private static final String MISSING = "f".repeat(40);

    @TempDir
    Path directory;

    @Test
    void initializationDefaultsToBftpAndIsReusable() throws Exception {
        try (var connection = new Connection(Map.of())) {
            var info = await(connection.client.runInit());
            assertFalse(info.supportsHTTP());
            assertEquals("", info.getHttpEndpoint());
            assertEquals(1, info.getMaxWorker());
            assertEquals(MTU, info.getTransportMTU());
            assertSame(info, await(connection.client.runInit()));
            assertTrue(await(connection.client.requestFiles(new String[0], ignored -> { })).files().isEmpty());
            assertEquals(0, connection.workerConnections.get());
        }
    }

    @Test
    void negotiatedHttpTransfersLargeSmallEmptyAndMissingWithoutWorkerConnection() throws Exception {
        byte[] data = new byte[8 * 1024 * 1024 + 23];
        new java.util.Random(17).nextBytes(data);
        Path source = Files.write(this.directory.resolve("source"), data);
        byte[] small = {1, 2, 3};
        Path smallSource = Files.write(this.directory.resolve("small"), small);
        Path emptySource = Files.write(this.directory.resolve("empty"), new byte[0]);
        var updates = new CopyOnWriteArrayList<TransferProgress>();
        try (var connection = new Connection(Map.of(hash(data), source, hash(small), smallSource,
                hash(new byte[0]), emptySource), 1)) {
            var info = await(connection.client.runInit());
            assertTrue(info.supportsHTTP());
            assertTrue(info.getHttpEndpoint().startsWith("http://127.0.0.1:"));
            var result = await(connection.client.requestFiles(new String[]{hash(data), hash(small), hash(new byte[0]), MISSING}, updates::add));
            assertArrayEquals(data, Files.readAllBytes(result.files().get(hash(data))));
            assertArrayEquals(small, Files.readAllBytes(result.files().get(hash(small))));
            assertEquals(0, Files.size(result.files().get(hash(new byte[0]))));
            assertEquals(List.of(MISSING), result.missingIds());
            assertEquals(0, connection.workerConnections.get());
            assertTrue(connection.connection.isActive());
            assertEquals(0, updates.get(0).receivedBytes());
            assertTrue(updates.get(updates.size() - 1).completed());
            assertEquals(data.length + small.length, updates.get(updates.size() - 1).receivedBytes());
            var second = await(connection.client.requestFiles(new String[]{hash(small)}, ignored -> { }));
            assertArrayEquals(small, Files.readAllBytes(second.files().get(hash(small))));
            assertEquals(0, connection.workerConnections.get());
            try (var files = Files.list(connection.downloads)) {
                assertTrue(files.noneMatch(file -> file.getFileName().toString().startsWith(".http-")));
            }
        }
    }

    @Test
    void attachedHttpServerIsUsedAndRemainsOwnedByCaller() throws Exception {
        byte[] data = {4, 5, 6};
        Path source = Files.write(this.directory.resolve("source"), data);
        HttpResourceServer borrowed;
        try (var connection = new Connection(Map.of(hash(data), source), 2)) {
            borrowed = connection.borrowedHttp;
            var result = await(connection.client.requestFiles(new String[]{hash(data)}, ignored -> { }));
            assertArrayEquals(data, Files.readAllBytes(result.files().get(hash(data))));
            assertEquals(0, connection.workerConnections.get());
            connection.server.close();
            await(connection.server.termination());
            assertTrue(borrowed.isRunning());
        }
        assertFalse(borrowed.isRunning());
    }

    @Test
    void ownedHttpListenerStopsWithRtpServer() throws Exception {
        try (var connection = new Connection(Map.of(), 1)) {
            var endpoint = java.net.URI.create(await(connection.client.runInit()).getHttpEndpoint());
            connection.server.close();
            await(connection.server.termination());
            try (var socket = new java.net.Socket()) {
                assertThrows(java.io.IOException.class, () -> socket.connect(
                        new java.net.InetSocketAddress(endpoint.getHost(), endpoint.getPort()), 1000));
            }
        }
    }

    @Test
    void httpDigestFailurePreservesExistingCacheAndDoesNotFallBackToWorker() throws Exception {
        byte[] data = {1, 2, 3};
        String id = hash(data);
        Path source = Files.write(this.directory.resolve("source"), data);
        try (var connection = new Connection(Map.of(id, source), 1)) {
            Files.createDirectories(connection.downloads);
            Files.writeString(connection.downloads.resolve(id), "previous cached file");
            var changed = new java.util.concurrent.atomic.AtomicBoolean();
            assertThrows(ExecutionException.class, () -> await(connection.client.requestFiles(new String[]{id}, update -> {
                if (changed.compareAndSet(false, true)) {
                    try {
                        Files.write(connection.storage.getResource(id).orElseThrow().toPath(), new byte[]{4, 5, 6});
                    } catch (java.io.IOException error) {
                        throw new java.io.UncheckedIOException(error);
                    }
                }
            })));
            assertEquals("previous cached file", Files.readString(connection.downloads.resolve(id)));
            assertEquals(0, connection.workerConnections.get());
            try (var files = Files.list(connection.downloads)) { assertEquals(1, files.count()); }
        }
    }

    @Test
    void stoppedHttpEndpointFailsBatchWithoutCreatingBftpWorker() throws Exception {
        byte[] data = {1, 2, 3};
        Path source = Files.write(this.directory.resolve("source"), data);
        try (var connection = new Connection(Map.of(hash(data), source), 2)) {
            connection.borrowedHttp.close();
            assertThrows(ExecutionException.class, () -> await(connection.client.requestFiles(new String[]{hash(data)}, ignored -> { })));
            assertEquals(0, connection.workerConnections.get());
            assertTrue(connection.connection.isActive());
        }
    }

    @Test
    void httpSelectionDoesNotRequireBftpMtuCompatibility() throws Exception {
        var capabilities = new P61_RTPProtocolInfo(1, MTU / 2, true, "http://127.0.0.1:1/");
        try (var connection = new Connection(Map.of(), capabilities)) {
            assertTrue(await(connection.client.runInit()).supportsHTTP());
            assertTrue(await(connection.client.requestFiles(new String[0], ignored -> { })).files().isEmpty());
            assertEquals(0, connection.workerConnections.get());
        }
    }

    @Test
    void cancellingUpgradedDownloadCleansStagingAndPreservesExternalMaster() throws Exception {
        interruptedHttpDownload(false);
    }

    @Test
    void masterDisconnectInterruptsUpgradedDownloadAndCleansStaging() throws Exception {
        interruptedHttpDownload(true);
    }

    private void interruptedHttpDownload(boolean disconnectMaster) throws Exception {
        byte[] data = new byte[MTU * 2];
        Path source = Files.write(this.directory.resolve("source"), data);
        try (var http = new StalledHttp(data.length)) {
            var info = new P61_RTPProtocolInfo(1, MTU, true, http.endpoint());
            try (var connection = new Connection(Map.of(hash(data), source), info)) {
                var pending = connection.client.requestFiles(new String[]{hash(data)}, ignored -> { });
                assertTrue(http.started.await(10, TimeUnit.SECONDS), "Expected HTTP data connection after negotiation");
                if (disconnectMaster) {
                    connection.connection.close().sync();
                    assertThrows(ExecutionException.class, () -> await(pending));
                    connection.initialized = false;
                } else {
                    assertTrue(pending.cancel(true));
                }
                connection.client.close();
                await(connection.client.termination());
                assertEquals(0, connection.workerConnections.get());
                if (!disconnectMaster) assertTrue(connection.connection.isActive());
                assertFalse(Files.exists(connection.downloads.resolve(hash(data))));
                try (var files = Files.list(connection.downloads)) { assertEquals(0, files.count()); }
            }
        }
    }

    /** Replies to the probe, then holds the body of the subsequent Range request until cancellation. */
    private static final class StalledHttp implements AutoCloseable {
        final com.sun.net.httpserver.HttpServer server;
        final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        StalledHttp(int size) throws Exception {
            this.server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            this.server.setExecutor(this.executor);
            this.server.createContext("/", exchange -> {
                try (exchange) {
                    String range = exchange.getRequestHeaders().getFirst("Range");
                    boolean probe = "bytes=0-1".equals(range);
                    int length = probe ? 2 : size;
                    exchange.getResponseHeaders().set("Content-Range", "bytes 0-" + (length - 1) + "/" + size);
                    exchange.sendResponseHeaders(206, length);
                    if (probe) {
                        exchange.getResponseBody().write(new byte[2]);
                    } else {
                        // A partial body ensures the client has an active stream to interrupt.
                        exchange.getResponseBody().write(new byte[1]);
                        exchange.getResponseBody().flush();
                        this.started.countDown();
                        try { this.release.await(); }
                        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                    }
                }
            });
            this.server.start();
        }

        String endpoint() { return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/"; }

        @Override
        public void close() {
            this.release.countDown();
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }

    @Test
    void incompatibleMtuFailsInitializationBeforeRequestingFiles() {
        var incompatible = new P61_RTPProtocolInfo(1, MTU / 2, false, "");
        var failure = assertThrows(ExecutionException.class, () -> new Connection(Map.of(), incompatible));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertFalse(Files.exists(this.directory.resolve("downloads")));
    }

    @Test
    void missingIdsAreReportedBeforeDataAndEveryOfferedFileIsCommitted() throws Exception {
        byte[] large = new byte[MTU * 3 + 17];
        new java.util.Random(42).nextBytes(large);
        Path small = Files.write(this.directory.resolve("small-source"), new byte[]{1, 2, 3});
        Path big = Files.write(this.directory.resolve("large-source"), large);
        String smallId = hash(new byte[]{1, 2, 3});
        String largeId = hash(large);
        var updates = new CopyOnWriteArrayList<TransferProgress>();
        try (var connection = new Connection(Map.of(smallId, small, largeId, big))) {
            var result = await(connection.client.requestFiles(new String[]{smallId, MISSING, largeId, smallId}, update -> {
                updates.add(update);
                if (updates.size() == 1) {
                    assertEquals(0, update.receivedBytes());
                    assertEquals(0, update.completedFiles());
                    assertEquals(List.of(MISSING), update.missingIds());
                    // The original import files are no longer needed; CAS objects stay pinned.
                    try {
                        Files.delete(small);
                        Files.delete(big);
                    } catch (java.io.IOException error) {
                        throw new RuntimeException(error);
                    }
                }
            }));
            assertEquals(List.of(MISSING), result.missingIds());
            assertEquals(1, connection.workerConnections.get());
            assertEquals(2, result.files().size());
            assertFalse(result.files().containsKey(MISSING));
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(result.files().get(smallId)));
            assertArrayEquals(large, Files.readAllBytes(result.files().get(largeId)));
            var last = updates.get(updates.size() - 1);
            assertEquals(large.length + 3, last.totalBytes());
            assertEquals(last.totalBytes(), last.receivedBytes());
            assertEquals(2, last.totalFiles());
            assertEquals(2, last.completedFiles());
            assertTrue(last.completed());
            assertTrue(updates.stream().allMatch(update -> update.missingIds().equals(List.of(MISSING))));
        }
    }

    @Test
    void allMissingIsSuccessfulEmptyTransferWithAnExplicitMissingList() throws Exception {
        var updates = new CopyOnWriteArrayList<TransferProgress>();
        try (var connection = new Connection(Map.of())) {
            var result = await(connection.client.requestFiles(new String[]{"a".repeat(40), "b".repeat(40)}, updates::add));
            assertTrue(result.files().isEmpty());
            assertEquals(List.of("a".repeat(40), "b".repeat(40)), result.missingIds());
            assertEquals(List.of(new TransferProgress(0, 0, 0, 0, List.of("a".repeat(40), "b".repeat(40)))), updates);
        }
    }

    @Test
    void sequentialBatchesReuseConnectionAndEmptyBatchIsValid() throws Exception {
        Path source = Files.write(this.directory.resolve("source"), new byte[0]);
        try (var connection = new Connection(Map.of(hash(new byte[0]), source))) {
            var first = await(connection.client.requestFiles(new String[]{hash(new byte[0])}, ignored -> { }));
            assertEquals(0, Files.size(first.files().get(hash(new byte[0]))));
            var second = await(connection.client.requestFiles(new String[0], ignored -> { }));
            assertTrue(second.files().isEmpty());
            assertTrue(second.missingIds().isEmpty());
        }
    }

    @Test
    void callbackFailureBeforeReadyAbortsWithoutPayloadOrFinalFiles() throws Exception {
        Path source = Files.write(this.directory.resolve("source"), new byte[100]);
        try (var connection = new Connection(Map.of(hash(new byte[100]), source))) {
            assertThrows(ExecutionException.class, () -> await(connection.client.requestFiles(new String[]{hash(new byte[100])}, update -> {
                throw new IllegalStateException("application callback failed");
            })));
            if (Files.exists(connection.downloads)) {
                try (var files = Files.list(connection.downloads)) { assertEquals(0, files.count()); }
            }
        }
    }

    @Test
    void corruptCasObjectRejectsBatchBeforePayload() throws Exception {
        byte[] data = {1, 2, 3};
        String id = hash(data);
        Path source = Files.write(this.directory.resolve("source"), data);
        try (var connection = new Connection(Map.of(id, source))) {
            Files.write(connection.storage.getResource(id).orElseThrow().toPath(), new byte[]{4, 5, 6});
            var updates = new CopyOnWriteArrayList<TransferProgress>();
            assertThrows(ExecutionException.class, () ->
                    await(connection.client.requestFiles(new String[]{id}, updates::add)));
            assertTrue(updates.isEmpty());
            assertEquals(0, connection.workerConnections.get());
        }
    }

    @Test
    void casRegistrationDeduplicatesAndReloadsLowercaseSha1Index() throws Exception {
        byte[] data = {1, 2, 3};
        String id = hash(data);
        Path source = Files.write(this.directory.resolve("source"), data);
        var root = new FilePath(this.directory.resolve("cas").toString());
        try (var storage = new CASResourceStorage(root)) {
            storage.registerResource(source.toFile());
            storage.registerResource(source.toFile());
        }
        try (var restored = new CASResourceStorage(root)) {
            assertArrayEquals(data, Files.readAllBytes(restored.getResource(id).orElseThrow().toPath()));
            assertTrue(Files.exists(source));
            assertThrows(IllegalArgumentException.class, () -> restored.getResource(id.toUpperCase(java.util.Locale.ROOT)));
        }
    }

    private final class Connection implements AutoCloseable {
        final NioEventLoopGroup boss = new NioEventLoopGroup(1);
        final NioEventLoopGroup worker = new NioEventLoopGroup(1);
        final NioEventLoopGroup sender = new NioEventLoopGroup(1);
        final Path downloads = directory.resolve("downloads");
        final CASResourceStorage storage = new CASResourceStorage(new FilePath(directory.resolve("cas").toString()));
        final RTPServer server;
        HttpResourceServer borrowedHttp;
        final RTPClient client = new RTPClient(new FilePath(this.downloads.toString()));
        Channel listener;
        Channel connection;
        boolean initialized;
        final java.util.concurrent.atomic.AtomicInteger workerConnections = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicReference<Channel> master = new java.util.concurrent.atomic.AtomicReference<>();

        Connection(Map<String, Path> catalogue) throws Exception {
            this(catalogue, null);
        }

        Connection(Map<String, Path> catalogue, P61_RTPProtocolInfo capabilities) throws Exception {
            this(catalogue, capabilities, 0);
        }

        Connection(Map<String, Path> catalogue, int httpMode) throws Exception {
            this(catalogue, null, httpMode);
        }

        Connection(Map<String, Path> catalogue, P61_RTPProtocolInfo capabilities, int httpMode) throws Exception {
            for (var source : catalogue.values()) storage.registerResource(source.toFile());
            if (httpMode == 2) {
                this.borrowedHttp = new HttpResourceServer(storage);
                this.borrowedHttp.start(0);
                this.server = new RTPServer(storage, this.borrowedHttp, "http://127.0.0.1:" + this.borrowedHttp.getLocalAddress().getPort());
            } else {
                this.server = capabilities == null ? new RTPServer(storage) : new RTPServer(storage, capabilities);
            }
            try {
                if (httpMode == 1) this.server.startHttp(0, "127.0.0.1");
                this.listener = new ServerBootstrap().group(this.boss, this.worker).channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) {
                                var codec = new NettyChannelInitializer();
                                codec.customLengthFrame(RTPProtocol.MAX_FRAME_SIZE);
                                codec.packet(RTPProtocol.PACKETS);
                                // Test-only association: one external Master, then its Workers.
                                codec.handler(() -> {
                                    if (master.compareAndSet(null, channel)) return server.masterHandler();
                                    workerConnections.incrementAndGet();
                                    assertNotSame(master.get(), channel);
                                    return server.workerHandler(master.get());
                                });
                                channel.pipeline().addLast(codec);
                            }
                        }).bind("127.0.0.1", 0).sync().channel();
                this.connection = new Bootstrap().group(this.sender).channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) {
                                var codec = new NettyChannelInitializer();
                                codec.customLengthFrame(RTPProtocol.MAX_FRAME_SIZE);
                                codec.packet(RTPProtocol.PACKETS);
                                codec.handler(client::masterHandler);
                                channel.pipeline().addLast(codec);
                            }
                        }).connect(this.listener.localAddress()).sync().channel();
                // Flush channelActive before invoking the public API from this application thread.
                this.connection.eventLoop().submit(() -> { }).sync();
                assertThrows(IllegalStateException.class, () -> client.requestFiles(new String[0], ignored -> { }));
                await(this.client.runInit());
                this.initialized = true;
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            this.client.close();
            assertTrue(!this.initialized || this.connection.isActive(), "Client must not close external Master");
            if (this.connection != null) this.connection.close().syncUninterruptibly();
            if (this.listener != null) this.listener.close().syncUninterruptibly();
            this.server.close();
            var a = this.sender.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            var b = this.worker.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            var c = this.boss.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            await(this.client.getTransferManager().termination());
            await(this.client.termination());
            await(this.server.termination());
            if (this.borrowedHttp != null && this.borrowedHttp.isRunning()) this.borrowedHttp.close();
            this.storage.close();
            assertTrue(a.await(10, TimeUnit.SECONDS));
            assertTrue(b.await(10, TimeUnit.SECONDS));
            assertTrue(c.await(10, TimeUnit.SECONDS));
            try (var objects = Files.walk(this.storage.getRoot().file().toPath())) {
                for (var object : objects.filter(Files::isRegularFile).toList()) {
                    assertTrue(Files.exists(object), "Cleanup must preserve CAS objects");
                }
            }
        }
    }
}
