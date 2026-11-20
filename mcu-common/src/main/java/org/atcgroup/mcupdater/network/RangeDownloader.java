package org.atcgroup.mcupdater.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class RangeDownloader implements AutoCloseable {
    public static final Logger LOGGER = LogManager.getLogger("MCU/RangeDownloader");

    private static final int DEFAULT_THREADS = 8;
    public static final int DEFAULT_TOTAL_THREADS = 96;
    private static final long DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024;
    private static final int MAX_RETRIES = 3;

    private final ExecutorService executor;
    private final HttpClient client;
    private final int threads;
    private final long chunkSize;
    private final Set<InputStream> activeStreams = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    private HttpResponse.BodyHandler<InputStream> streamingBody() {
        return info -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofInputStream(), stream -> {
            var tracked = new java.io.FilterInputStream(stream) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        activeStreams.remove(this);
                    }
                }
            };
            this.activeStreams.add(tracked);
            if (this.closed) {
                try {
                    tracked.close();
                } catch (IOException ignored) {
                }
            }
            return tracked;
        });
    }

    public RangeDownloader() {
        this(DEFAULT_THREADS, DEFAULT_TOTAL_THREADS, DEFAULT_CHUNK_SIZE);
    }

    public RangeDownloader(int threads, int totalThreads, long chunkSize) {
        if (threads <= 0) throw new IllegalArgumentException("threads must be > 0");
        if (totalThreads <= 0) throw new IllegalArgumentException("totalThreads must be > 0");

        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");

        this.threads = threads;
        this.chunkSize = chunkSize;

        this.executor = Executors.newFixedThreadPool(totalThreads);
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * 下载文件。
     *
     * @param url      文件 URL
     * @param target   最终文件路径
     * @param progress 下载进度回调
     */
    public void download(String url, Path target, ProgressListener progress) throws IOException, InterruptedException {
        Objects.requireNonNull(url);
        Objects.requireNonNull(target);
        Objects.requireNonNull(progress);
        if (this.closed) throw new IOException("Downloader closed");

        Files.createDirectories(target.toAbsolutePath().getParent());
        Path partFile = target.resolveSibling(target.getFileName() + ".part");
        Path resumeFile = target.resolveSibling(target.getFileName() + ".resume");


        FileInfo info;
        var attempt = 0;

        while (true) {
            try {
                info = probe(url);
                break;
            } catch (IOException e) {
                attempt++;
                LOGGER.warn("[RangeDownloader] Probe failed (attempt {}/3): {}", attempt, e.getMessage());

                if (attempt <= 3) {
                    Thread.sleep(500L * attempt);
                    continue;
                }

                throw e;
            }
        }

        if (!info.rangeSupported) {
            LOGGER.warn("[RangeDownloader] Server does not support Range, falling back to single-thread download.");

            singleThreadDownload(url, target, partFile, resumeFile, info, progress);

            return;
        }

        rangeDownload(url, target, partFile, resumeFile, info, progress);
    }

    /**
     * 探测服务器。
     * <p>
     * 优先使用 Range: bytes=0-0。
     * <p>
     * 如果得到：
     * <p>
     * 206 Partial Content
     * Content-Range: bytes 0-0/TOTAL
     * <p>
     * 则认为服务器支持 Range。
     */
    private FileInfo probe(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Range", "bytes=0-1").GET().build();
        var response = client.send(request, streamingBody());

        try (var ignored = response.body()) {
            var status = response.statusCode();

            if (status == 206) {

                var contentRange = response.headers().firstValue("Content-Range").orElse(null);

                if (contentRange == null) {
                    throw new IOException("Server returned 206 but no Content-Range");
                }

                long total = parseTotalSize(contentRange);

                return new FileInfo(total, true);
            }

            if (status == 416 && "bytes */0".equals(response.headers().firstValue("Content-Range").orElse(""))) {
                return new FileInfo(0, false);
            }

            /*
             * 有些服务器虽然不支持 Range，
             * 但会正常返回 200。
             *
             * 这时候必须获取 Content-Length。
             */
            if (status == 200) {

                Optional<String> length = response.headers().firstValue("Content-Length");

                if (length.isEmpty()) {
                    throw new IOException("Server returned 200 but no Content-Length");
                }

                long total = Long.parseLong(length.get());

                return new FileInfo(total, false);
            }

            throw new IOException("Range probe failed: HTTP " + status);
        }
    }

    private long parseTotalSize(String contentRange) throws IOException {

        /*
         * bytes 0-0/123456
         */

        int slash = contentRange.lastIndexOf('/');

        if (slash < 0) {
            throw new IOException("Invalid Content-Range: " + contentRange);
        }

        String total = contentRange.substring(slash + 1).trim();

        if ("*".equals(total)) {
            throw new IOException("Unknown total size: " + contentRange);
        }

        try {
            return Long.parseLong(total);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Content-Range: " + contentRange, e);
        }
    }

    // ---------------------------------------------------------------------
    // Range download
    // ---------------------------------------------------------------------

    private void rangeDownload(String url, Path target, Path partFile, Path resumeFile, FileInfo info, ProgressListener progress) throws IOException, InterruptedException {
        long total = info.size;

        int chunkCount = (int) ((total + chunkSize - 1) / chunkSize);

        ResumeState state = ResumeState.load(resumeFile, url, total, chunkSize, chunkCount);

        /*
         * 如果 .part 不存在，预分配文件。
         */
        if (!Files.exists(partFile)) {

            try (RandomAccessFile raf = new RandomAccessFile(partFile.toFile(), "rw")) {

                raf.setLength(total);
            }

        } else {

            /*
             * 文件存在但大小不对，直接重新建立。
             */
            long actualSize = Files.size(partFile);

            if (actualSize != total) {

                LOGGER.warn("[RangeDownloader] Invalid partial file size, recreating.");

                try (RandomAccessFile raf = new RandomAccessFile(partFile.toFile(), "rw")) {

                    raf.setLength(total);
                }

                state.reset();
            }
        }

        var downloaded = new AtomicLong(state.totalDownloaded());
        var queue = new LinkedBlockingQueue<Chunk>();

        progress.onProgress(downloaded.get(), total);

        for (int i = 0; i < chunkCount; i++) {
            if (!state.isCompleted(i)) {
                var start = i * chunkSize;
                var end = Math.min(total - 1, start + chunkSize - 1);

                queue.add(new Chunk(i, start, end));
            }
        }

        if (queue.isEmpty()) {
            finish(partFile, target, resumeFile);
            return;
        }

        var futures = new ArrayList<Future<?>>();
        var gate = new Object();
        var stopping = new java.util.concurrent.atomic.AtomicBoolean();
        var running = new Phaser(1);

        try {
            for (int i = 0; i < Math.min(this.threads, queue.size()); i++) {
                futures.add(this.executor.submit(() -> {
                    synchronized (gate) {
                        if (stopping.get()) return;
                        running.register();
                    }
                    try {
                        while (true) {
                            if (Thread.currentThread().isInterrupted() || stopping.get()) return;
                            var chunk = queue.poll();

                            if (chunk == null) {
                                return;
                            }

                            try {
                                downloadChunk(url, partFile, state, chunk, downloaded, total, progress);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } finally {
                        running.arriveAndDeregister();
                    }
                }));
            }

            for (var future : futures) {
                try {
                    future.get();

                } catch (ExecutionException e) {
                    var cause = e.getCause();

                    if (cause instanceof IOException io) {
                        throw io;
                    }

                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }

                    throw new IOException("Download worker failed", cause);
                }
            }

        } finally {
            synchronized (gate) {
                stopping.set(true);
            }
            futures.forEach(future -> future.cancel(true));
            // Wait for actual IO completion before a caller cleans staging files.
            running.arriveAndAwaitAdvance();
            /*
             * 即使失败，也保存当前状态。
             */
            state.save(resumeFile);
        }

        if (!state.allCompleted()) {
            throw new IOException("Download finished but some chunks are incomplete.");
        }

        finish(partFile, target, resumeFile);
    }

    private void downloadChunk(String url, Path partFile, ResumeState state, Chunk chunk, AtomicLong downloaded, long total, ProgressListener progress) throws IOException {

        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Download interrupted");

            try {

                downloadChunkOnce(url, partFile, state, chunk, downloaded, total, progress);

                return;

            } catch (IOException e) {

                lastException = e;

                LOGGER.warn("[RangeDownloader] Chunk {} failed (attempt {}/{}): {}", chunk.index, attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {

                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ex) {

                        Thread.currentThread().interrupt();

                        throw new IOException("Download interrupted", ex);
                    }
                }
            }
        }

        throw new IOException("Chunk " + chunk.index + " failed after " + MAX_RETRIES + " attempts", lastException);
    }

    private void downloadChunkOnce(String url, Path partFile, ResumeState state, Chunk chunk, AtomicLong downloaded, long total, ProgressListener progress) throws IOException {
        long current = state.getDownloaded(chunk.index);
        long requestStart = chunk.start + current;

        if (requestStart > chunk.end) {

            state.markCompleted(chunk.index);

            return;
        }

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(30)).header("Range", "bytes=" + requestStart + "-" + chunk.end).header("Accept-Encoding", "identity").GET().build();

        HttpResponse<InputStream> response;

        try {

            response = client.send(request, streamingBody());

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IOException("Download interrupted", e);
        }

        int status = response.statusCode();

        if (status != 206) {

            try {
                response.body().close();
            } catch (IOException ignored) {
            }

            throw new IOException("Expected HTTP 206, got " + status);
        }

        try (InputStream input = response.body();

             RandomAccessFile raf = new RandomAccessFile(partFile.toFile(), "rw")) {
            validateContentRange(response, requestStart, chunk.end);

            raf.seek(requestStart);

            byte[] buffer = new byte[128 * 1024];

            long position = requestStart;

            long lastPersist = System.nanoTime();

            int read;

            while ((read = input.read(buffer)) != -1) {

                if (Thread.currentThread().isInterrupted()) {

                    throw new IOException("Download interrupted");
                }

                long remaining = chunk.end - position + 1;

                if (read > remaining) {

                    throw new IOException("Server returned more data " + "than requested");
                }

                raf.write(buffer, 0, read);

                position += read;

                state.addDownloaded(chunk.index, read);
                downloaded.addAndGet(read);
                progress.onProgress(downloaded.get(), total);

                /*
                 * 不要每次 read 都写 resume 文件。
                 *
                 * 每约 1 秒保存一次。
                 */
                long now = System.nanoTime();

                if (now - lastPersist >= 1_000_000_000L) {
                    state.save(state.resumeFile);
                    lastPersist = now;
                }
            }

            long expected = chunk.end - requestStart + 1;
            long actual = position - requestStart;

            if (actual != expected) {

                throw new IOException("Incomplete chunk: expected " + expected + " bytes, got " + actual);
            }
        }

        state.markCompleted(chunk.index);
        state.save(state.resumeFile);
    }

    private void validateContentRange(HttpResponse<?> response, long expectedStart, long expectedEnd) throws IOException {

        String range = response.headers().firstValue("Content-Range").orElseThrow(() -> new IOException("Missing Content-Range"));

        /*
         * bytes START-END/TOTAL
         */
        String value = range.trim();

        if (!value.startsWith("bytes ")) {

            throw new IOException("Invalid Content-Range: " + range);
        }

        String rangePart = value.substring(6);

        int dash = rangePart.indexOf('-');

        int slash = rangePart.indexOf('/');

        if (dash < 0 || slash < 0) {

            throw new IOException("Invalid Content-Range: " + range);
        }

        long actualStart;
        long actualEnd;

        try {

            actualStart = Long.parseLong(rangePart.substring(0, dash));

            actualEnd = Long.parseLong(rangePart.substring(dash + 1, slash));

        } catch (NumberFormatException e) {

            throw new IOException("Invalid Content-Range: " + range, e);
        }

        if (actualStart != expectedStart || actualEnd != expectedEnd) {

            throw new IOException("Wrong Content-Range: expected " + expectedStart + "-" + expectedEnd + ", got " + actualStart + "-" + actualEnd);
        }
    }

    // ---------------------------------------------------------------------
    // Single-thread fallback
    // ---------------------------------------------------------------------

    private void singleThreadDownload(String url, Path target, Path partFile, Path resumeFile, FileInfo info, ProgressListener progress) throws IOException, InterruptedException {

        long total = info.size;

        /*
         * 对不支持 Range 的服务器，
         * 无法安全地对中间位置恢复。
         *
         * 因此从头下载。
         */
        Files.deleteIfExists(resumeFile);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(30)).header("Accept-Encoding", "identity").GET().build();

        HttpResponse<InputStream> response = client.send(request, streamingBody());

        if (response.statusCode() / 100 != 2) {

            response.body().close();

            throw new IOException("Download failed: HTTP " + response.statusCode());
        }

        long downloaded = 0;

        try (InputStream input = response.body();

             OutputStream output = Files.newOutputStream(partFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            byte[] buffer = new byte[128 * 1024];

            int read;

            while ((read = input.read(buffer)) != -1) {

                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Download interrupted");
                output.write(buffer, 0, read);

                downloaded += read;

                progress.onProgress(downloaded, total);
            }
        }

        if (downloaded != total) {

            throw new IOException("Expected " + total + " bytes, got " + downloaded);
        }

        finish(partFile, target, resumeFile);
    }

    // ---------------------------------------------------------------------
    // Finish
    // ---------------------------------------------------------------------

    private void finish(Path partFile, Path target, Path resumeFile) throws IOException {

        // Replace on move, so a failed move does not first delete the previous target.
        try {

            Files.move(partFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        } catch (AtomicMoveNotSupportedException e) {

            Files.move(partFile, target, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.deleteIfExists(resumeFile);

        LOGGER.info("[RangeDownloader] Download complete: {}", target);
    }

    @Override
    public void close() {
        this.closed = true;
        this.executor.shutdownNow();
        for (var stream : this.activeStreams) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public interface ProgressListener {

        /**
         * @param downloaded 当前已下载字节数
         * @param total      文件总大小
         */
        void onProgress(long downloaded, long total);
    }

    // ---------------------------------------------------------------------
    // Internal classes
    // ---------------------------------------------------------------------

    private record FileInfo(long size, boolean rangeSupported) {
    }

    private record Chunk(int index, long start, long end) {
    }

    private static class ResumeState {

        private final String url;
        private final long totalSize;
        private final long chunkSize;
        private final long[] downloaded;
        private final boolean[] completed;

        private final Path resumeFile;

        private ResumeState(String url, long totalSize, long chunkSize, int chunkCount, Path resumeFile) {

            this.url = url;
            this.totalSize = totalSize;
            this.chunkSize = chunkSize;
            this.resumeFile = resumeFile;

            this.downloaded = new long[chunkCount];

            this.completed = new boolean[chunkCount];
        }

        static ResumeState load(Path resumeFile, String url, long totalSize, long chunkSize, int chunkCount) throws IOException {

            ResumeState state = new ResumeState(url, totalSize, chunkSize, chunkCount, resumeFile);

            if (!Files.exists(resumeFile)) {
                return state;
            }

            Properties properties = new Properties();

            try (InputStream input = Files.newInputStream(resumeFile)) {

                properties.load(input);
            }

            /*
             * URL / 文件大小 / chunk size
             * 任何一个发生变化，都不能继续。
             */
            if (!url.equals(properties.getProperty("url"))) {
                return state;
            }

            if (totalSize != Long.parseLong(properties.getProperty("totalSize", "-1"))) {
                return state;
            }

            if (chunkSize != Long.parseLong(properties.getProperty("chunkSize", "-1"))) {
                return state;
            }

            for (int i = 0; i < chunkCount; i++) {

                state.downloaded[i] = Long.parseLong(properties.getProperty("chunk." + i + ".downloaded", "0"));

                state.completed[i] = Boolean.parseBoolean(properties.getProperty("chunk." + i + ".completed", "false"));
            }

            return state;
        }

        synchronized void save(Path file) throws IOException {

            Properties properties = new Properties();

            properties.setProperty("url", url);

            properties.setProperty("totalSize", Long.toString(totalSize));

            properties.setProperty("chunkSize", Long.toString(chunkSize));

            for (int i = 0; i < downloaded.length; i++) {

                properties.setProperty("chunk." + i + ".downloaded", Long.toString(downloaded[i]));

                properties.setProperty("chunk." + i + ".completed", Boolean.toString(completed[i]));
            }

            Path temp = file.resolveSibling(file.getFileName() + ".tmp");

            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

                properties.store(output, "RangeDownloader resume state");
            }

            try {

                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        synchronized void reset() {

            Arrays.fill(downloaded, 0);

            Arrays.fill(completed, false);
        }

        synchronized long getDownloaded(int chunk) {

            return downloaded[chunk];
        }

        synchronized void addDownloaded(int chunk, long amount) {

            downloaded[chunk] += amount;
        }

        synchronized void markCompleted(int chunk) {

            completed[chunk] = true;
        }

        synchronized boolean isCompleted(int chunk) {

            return completed[chunk];
        }

        synchronized boolean allCompleted() {

            for (boolean value : completed) {

                if (!value) return false;
            }

            return true;
        }

        synchronized long totalDownloaded() {

            long result = 0;

            for (long value : downloaded) {
                result += value;
            }

            return result;
        }
    }
}
