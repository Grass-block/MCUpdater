package me.gb2022.mcupdater.server.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.protocol.MCUProtocol;
import me.gb2022.mcupdater.rtp.RTPClient;
import me.gb2022.mcupdater.rtp.RTPServer;
import me.gb2022.mcupdater.rtp.protocol.RTPProtocol;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import me.gb2022.simpnet.channel.NettyChannelInitializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Explicitly invoked benchmark, deliberately not a JUnit test. */
public interface BenchmarkClient {
    long MIB = 1024L * 1024;
    long MAX_FILE = 256 * MIB;
    int FILE_COUNT = 256;
    long TOTAL_BYTES = 16 * 1024 * MIB;
    record Resource(String id, long size) { }

    static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: BenchmarkClient [bftp|http]");
        }
        String mode = args.length == 0 ? "bftp" : args[0];
        Path parent = Path.of(System.getProperty("user.dir")).resolve("rtp-benchmark");
        if (!mode.equals("bftp") && !mode.equals("http")) {
            throw new IllegalArgumentException("Mode must be bftp or http");
        }
        long seed = System.currentTimeMillis();
        Files.createDirectories(parent);
        // Each invocation gets new CAS and download directories; no previous cache hits.
        Path directory = Files.createTempDirectory(parent, mode + "-");
        System.out.println("Benchmark directory: " + directory);
        long required = 2 * TOTAL_BYTES + MAX_FILE + 1024 * MIB;
        if (Files.getFileStore(directory).getUsableSpace() < required) {
            throw new IOException("Need at least 33.25 GiB free for CAS, downloads and staging");
        }
        String environment = "started=" + Instant.now() + "\nmode=" + mode + "\nseed=" + seed
                + "\nfiles=" + FILE_COUNT + "\nbytes=" + TOTAL_BYTES
                + "\njava=" + System.getProperty("java.version")
                + "\nos=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors() + "\n";
        Files.writeString(directory.resolve("environment.txt"), environment);
        try {
            List<Resource> resources;
            long generationStart = System.nanoTime();
            try (var storage = new CASResourceStorage(new FilePath(directory.resolve("cas").toString()))) {
                resources = generate(storage, directory, seed);
            }
            double generationSeconds = secondsSince(generationStart);
            try (var connection = new Connection(directory)) {
                connection.connect(mode);
                System.out.printf(Locale.ROOT,
                        "Prepared %d files, %.0f MiB, average %.2f MiB in %.2f s. Starting %s loopback transfer.%n",
                        resources.size(), TOTAL_BYTES / (double) MIB,
                        TOTAL_BYTES / (double) FILE_COUNT / MIB, generationSeconds, mode);
                var downloaded = new ArrayList<Path>();
                try (var csv = Files.newBufferedWriter(directory.resolve("files.csv"))) {
                    csv.write("index,sha1,bytes,seconds,mib_per_second\n");
                    long start = System.nanoTime();
                    long completedBytes = 0;
                    var progress = new Progress(start);
                    for (int i = 0; i < resources.size(); i++) {
                        var resource = resources.get(i);
                        long previousBytes = completedBytes;
                        int previousFiles = i;
                        long fileStart = System.nanoTime();
                        // One file per batch avoids hashing all 16 GiB before the manifest timeout.
                        // Keep the same Master connection and submit the next request without a pause.
                        var result = connection.client.requestFiles(new String[]{resource.id()}, update ->
                                                       progress.report(previousBytes + update.receivedBytes(),
                                                               previousFiles + update.completedFiles(), false))
                                                      .get(30, TimeUnit.MINUTES);
                        double elapsed = secondsSince(fileStart);
                        if (!result.missingIds().isEmpty() || !result.files().containsKey(resource.id())) {
                            throw new IOException("Missing downloaded resource: " + resource.id());
                        }
                        downloaded.add(result.files().get(resource.id()));
                        completedBytes += resource.size();
                        csv.write(String.format(Locale.ROOT, "%d,%s,%d,%.6f,%.3f%n",
                                i + 1, resource.id(), resource.size(), elapsed, resource.size() / (double) MIB / elapsed));
                    }
                    double elapsed = secondsSince(start);
                    progress.report(completedBytes, FILE_COUNT, true);
                    // Independent post-run verification is outside the measured transfer interval.
                    for (int i = 0; i < resources.size(); i++) {
                        var resource = resources.get(i);
                        Path file = downloaded.get(i);
                        if (Files.size(file) != resource.size() || !sha1(file).equals(resource.id())) {
                            throw new IOException("Verification failed: " + file);
                        }
                    }
                    String summary = String.format(Locale.ROOT,
                            "status=PASS%nfiles=%d%nbytes=%d%ntransfer_seconds=%.6f%nthroughput_mib_s=%.3f%n"
                                    + "throughput_mbit_s=%.3f%ngeneration_seconds=%.3f%n",
                            FILE_COUNT, completedBytes, elapsed, completedBytes / (double) MIB / elapsed,
                            completedBytes * 8.0 / 1_000_000 / elapsed, generationSeconds);
                    Files.writeString(directory.resolve("summary.txt"), summary);
                    System.out.println(summary);
                }
            }
        } catch (Exception failure) {
            Files.writeString(directory.resolve("failure.txt"), failure.toString());
            throw failure;
        }
        System.out.println("Reports and generated data retained at: " + directory);
    }

    static void runServer(Path directory, boolean http) throws Exception {
        Path portFile = directory.resolve("server.port");
        Path stopFile = directory.resolve("server.stop");
        var storage = new CASResourceStorage(new FilePath(directory.resolve("cas").toString()));
        var server = new RTPServer(storage);
        server.setMaxWorkers(4);
        var boss = new NioEventLoopGroup(1);
        var workers = new NioEventLoopGroup(1);
        var master = new AtomicReference<Channel>();
        Channel listener = null;
        try {
            if (http) server.startHttp(0, "127.0.0.1");
            var bootstrap = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class);
            MCUProtocol.serverOptions(bootstrap);
            var netty = bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel(SocketChannel channel) {
                    var codec = new NettyChannelInitializer();
                    codec.customLengthFrame(RTPProtocol.MAX_FRAME_SIZE);
                    codec.packet(RTPProtocol.PACKETS);
                    codec.handler(() -> master.compareAndSet(null, channel)
                            ? server.masterHandler() : server.workerHandler(master.get()));
                    channel.pipeline().addLast(codec);
                }
            }).bind("127.0.0.1", 0).sync().channel();
            listener = netty;
            Files.writeString(portFile, Integer.toString(((java.net.InetSocketAddress) netty.localAddress()).getPort()));
            while (!Files.exists(stopFile)) Thread.sleep(100);
        } finally {
            if (listener != null) listener.close().syncUninterruptibly();
            server.close();
            server.termination().toCompletableFuture().get(30, TimeUnit.SECONDS);
            boss.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            workers.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            storage.close();
        }
    }

    private static List<Resource> generate(CASResourceStorage storage, Path directory, long seed) throws Exception {
        var random = new Random(seed);
        var sizes = new ArrayList<Long>();
        // 64 groups of four files, each group sums to 256 MiB. Exact total and average.
        // The first group guarantees empty-file and maximum-size boundary coverage.
        sizes.addAll(List.of(0L, 0L, 0L, MAX_FILE));
        for (int group = 1; group < FILE_COUNT / 4; group++) {
            long[] cuts = {0, random.nextLong(MAX_FILE + 1), random.nextLong(MAX_FILE + 1),
                    random.nextLong(MAX_FILE + 1), MAX_FILE};
            Arrays.sort(cuts);
            for (int i = 1; i < cuts.length; i++) sizes.add(cuts[i] - cuts[i - 1]);
        }
        Collections.shuffle(sizes, random);
        var resources = new ArrayList<Resource>();
        byte[] buffer = new byte[(int) MIB];
        Path scratch = directory.resolve("generating.bin");
        try (var manifest = Files.newBufferedWriter(directory.resolve("dataset.csv"))) {
            manifest.write("index,sha1,bytes\n");
            for (int i = 0; i < sizes.size(); i++) {
                long size = sizes.get(i);
                var digest = MessageDigest.getInstance("SHA-1");
                try (var output = Files.newOutputStream(scratch)) {
                    for (long remaining = size; remaining > 0;) {
                        random.nextBytes(buffer);
                        int length = (int) Math.min(buffer.length, remaining);
                        output.write(buffer, 0, length);
                        digest.update(buffer, 0, length);
                        remaining -= length;
                    }
                }
                String id = HexFormat.of().formatHex(digest.digest());
                storage.registerResource(scratch.toFile());
                resources.add(new Resource(id, size));
                manifest.write((i + 1) + "," + id + "," + size + "\n");
                System.out.printf(Locale.ROOT, "Prepared %d/%d (%.2f MiB)%n", i + 1, FILE_COUNT, size / (double) MIB);
            }
        } finally {
            Files.deleteIfExists(scratch);
        }
        return resources;
    }

    private static String sha1(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-1");
        byte[] buffer = new byte[(int) MIB];
        try (var input = Files.newInputStream(file)) {
            for (int length; (length = input.read(buffer)) != -1;) digest.update(buffer, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static double secondsSince(long start) {
        return (System.nanoTime() - start) / 1_000_000_000.0;
    }

    final class Progress {
        private final long start;
        private long lastTime;
        private long lastBytes;

        Progress(long start) { this.start = this.lastTime = start; }

        synchronized void report(long bytes, int files, boolean force) {
            long now = System.nanoTime();
            if (!force && now - lastTime < TimeUnit.SECONDS.toNanos(5)) return;
            System.out.printf(Locale.ROOT,
                    "%.1f%% | %d/%d files | %.1f MiB | interval %.2f MiB/s | average %.2f MiB/s%n",
                    bytes * 100.0 / TOTAL_BYTES, files, FILE_COUNT, bytes / (double) MIB,
                    (bytes - lastBytes) / (double) MIB / ((now - lastTime) / 1e9),
                    bytes / (double) MIB / ((now - start) / 1e9));
            lastTime = now;
            lastBytes = bytes;
        }
    }

    final class Connection implements AutoCloseable {
        private final NioEventLoopGroup sender = new NioEventLoopGroup(1);
        private final Path directory;
        private Process serverProcess;
        private RTPClient client;
        private Channel masterChannel;

        Connection(Path directory) { this.directory = directory; }

        void connect(String mode) throws Exception {
            boolean http = mode.equals("http");
            Path portFile = directory.resolve("server.port");
            Path stopFile = directory.resolve("server.stop");
            var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
            if (!Files.exists(javaExecutable)) javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            var command = new ArrayList<String>();
            command.add(javaExecutable.toString());
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(BenchmarkServer.class.getName());
            command.add(mode);
            serverProcess = new ProcessBuilder(command).redirectErrorStream(true)
                    .directory(directory.toFile())
                    .redirectOutput(directory.resolve("server.log").toFile()).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!Files.exists(portFile)) {
                if (!serverProcess.isAlive()) throw new IOException("RTP server process exited; inspect " + directory.resolve("server.log"));
                if (System.nanoTime() >= deadline) throw new IOException("Timed out waiting for RTP server process");
                Thread.sleep(50);
            }
            int port = Integer.parseInt(Files.readString(portFile).trim());
            client = new RTPClient(new FilePath(directory.resolve("downloads").toString()));
            var bootstrap = new Bootstrap().group(sender).channel(NioSocketChannel.class);
            MCUProtocol.clientOptions(bootstrap);
            masterChannel = bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel(SocketChannel socket) {
                    var codec = new NettyChannelInitializer();
                    codec.customLengthFrame(RTPProtocol.MAX_FRAME_SIZE);
                    codec.packet(RTPProtocol.PACKETS);
                    codec.handler(client::masterHandler);
                    socket.pipeline().addLast(codec);
                }
            }).connect("127.0.0.1", port).sync().channel();
            masterChannel.eventLoop().submit(() -> { }).sync();
            var info = client.runInit().toCompletableFuture().get(30, TimeUnit.SECONDS);
            if (info.supportsHTTP() != http) throw new IllegalStateException("Unexpected negotiated transport");
        }

        @Override public void close() throws Exception {
            try {
                if (client != null) client.close();
                if (masterChannel != null) masterChannel.close().syncUninterruptibly();
                if (client != null) client.termination().toCompletableFuture().get(30, TimeUnit.SECONDS);
                if (serverProcess != null) {
                    Files.writeString(directory.resolve("server.stop"), "stop");
                    if (!serverProcess.waitFor(30, TimeUnit.SECONDS)) {
                        serverProcess.destroyForcibly();
                        throw new IOException("RTP server process did not stop cleanly");
                    }
                    if (serverProcess.exitValue() != 0) throw new IOException("RTP server process failed; inspect " + directory.resolve("server.log"));
                }
            } finally {
                var a = sender.shutdownGracefully(0, 5, TimeUnit.SECONDS);
                a.syncUninterruptibly();
            }
        }
    }
}
