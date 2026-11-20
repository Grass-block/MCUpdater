package org.atcgroup.mcupdater.server.v4.rtp;

import io.netty.channel.Channel;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P62_RTPBatchRequest;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P63_RTPBatchResponse;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P64_RTPBatchRelease;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.P71_FTPInlineRequest;
import org.atcgroup.mcupdater.network.RangeDownloader;
import org.atcgroup.mcupdater.util.FileDigest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Receives only files explicitly offered by the server's complete manifest. */
final class ResourceTransferBatch {
    private final String batchId = UUID.randomUUID().toString();
    private final TransferManager manager;
    private final Channel channel;
    private final java.util.function.Supplier<CompletableFuture<Channel>> connectWorker;
    private Channel worker;
    private final Path directory;
    private final Consumer<TransferProgress> progress;
    private final List<String> requested;
    private final Map<String, Path> files = new LinkedHashMap<>();
    private final CompletableFuture<TransferBatchResult> future = new CompletableFuture<>();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private final ScheduledExecutorService executor;
    private final Duration timeout = Duration.ofMinutes(2);
    private P63_RTPBatchResponse manifest;
    private TransferFileReceiver active;
    private long totalBytes;
    private long committedBytes;
    private ScheduledFuture<?> ticker;
    private ScheduledFuture<?> manifestTimeout;
    private volatile boolean stopping;
    private final String httpEndpoint;
    private final java.util.concurrent.ExecutorService httpExecutor;
    private final RangeDownloader downloader;
    private java.util.concurrent.Future<?> httpTask;
    private Path httpStaging;
    private volatile long httpBytes;
    private volatile long httpActivity;

    ResourceTransferBatch(TransferManager manager, Channel channel, Path directory, String[] resourceIds,
                          Consumer<TransferProgress> progress, java.util.function.Supplier<CompletableFuture<Channel>> connectWorker,
                          String httpEndpoint) {
        this.manager = manager;
        this.connectWorker = connectWorker;
        this.channel = channel;
        this.directory = directory.toAbsolutePath().normalize();
        this.progress = java.util.Objects.requireNonNull(progress, "progress");
        this.requested = List.copyOf(new LinkedHashSet<>(List.of(resourceIds.clone())));
        // Validate before starting a thread or putting a request on the wire.
        new P62_RTPBatchRequest(this.batchId, this.requested);
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "transfer-batch"));
        this.httpEndpoint = httpEndpoint == null ? null : httpEndpoint.endsWith("/") ? httpEndpoint : httpEndpoint + "/";
        this.httpExecutor = httpEndpoint == null ? null : Executors.newSingleThreadExecutor(r -> new Thread(r, "transfer-http"));
        this.downloader = httpEndpoint == null ? null : new RangeDownloader(4, 4, 8L * 1024 * 1024);
    }

    String getBatchId() { return this.batchId; }

    java.util.concurrent.CompletionStage<Void> termination() { return this.termination.minimalCompletionStage(); }

    CompletableFuture<TransferBatchResult> start() {
        this.future.whenComplete((result, error) -> {
            if (this.future.isCancelled()) execute(() -> fail(new CancellationException("Batch cancelled")));
        });
        this.executor.execute(() -> {
            if (this.future.isCancelled()) return;
            this.manifestTimeout = this.executor.schedule(() -> fail(new java.util.concurrent.TimeoutException(
                    "Resource manifest timed out")), this.timeout.toMillis(), TimeUnit.MILLISECONDS);
            this.channel.writeAndFlush(new P62_RTPBatchRequest(this.batchId, this.requested))
                    .addListener(sent -> {
                        if (!sent.isSuccess()) disconnected(sent.cause());
                    });
        });
        return this.future;
    }

    void manifest(P63_RTPBatchResponse manifest) {
        execute(() -> {
            if (this.stopping || this.future.isDone()) return;
            try {
                if (this.manifest != null || manifest.isRejected()) throw new IllegalStateException("Batch rejected or repeated manifest");
                var partition = new java.util.HashSet<>(manifest.getMissingIds());
                manifest.getFiles().forEach(file -> partition.add(file.resourceId()));
                if (!partition.equals(new java.util.HashSet<>(this.requested))) {
                    throw new IllegalArgumentException("Server manifest does not account for every requested resource");
                }
                this.manifest = manifest;
                this.manifestTimeout.cancel(false);
                for (var file : manifest.getFiles()) this.totalBytes = Math.addExact(this.totalBytes, file.size());
                // This callback is deliberately before READY or any range request.
                report();
                this.ticker = this.executor.scheduleWithFixedDelay(() -> {
                    try {
                        if (!this.stopping) {
                            if (this.httpTask != null && System.nanoTime() - this.httpActivity > this.timeout.toNanos()) {
                                throw new java.util.concurrent.TimeoutException("HTTP resource transfer timed out");
                            }
                            report();
                        }
                    } catch (Throwable error) {
                        fail(error);
                    }
                }, 100, 100, TimeUnit.MILLISECONDS);
                if (manifest.getFiles().isEmpty() || this.httpEndpoint != null) {
                    receive(0);
                } else {
                    this.connectWorker.get().whenComplete((worker, error) -> {
                        try {
                            this.executor.execute(() -> {
                                if (this.stopping || this.future.isDone()) {
                                    if (worker != null) worker.close();
                                } else if (error != null) {
                                    fail(error);
                                } else {
                                    this.worker = worker;
                                    receive(0);
                                }
                            });
                        } catch (java.util.concurrent.RejectedExecutionException stopped) {
                            if (worker != null) worker.close();
                        }
                    });
                }
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    private void receive(int index) {
        if (this.future.isDone() || this.stopping) return;
        if (index == this.manifest.getFiles().size()) {
            this.stopping = true;
            stopTimers();
            this.channel.writeAndFlush(new P64_RTPBatchRelease(this.batchId));
            if (this.worker != null) this.worker.close();
            closeHttp();
            this.executor.shutdown();
            this.termination.complete(null);
            this.future.complete(new TransferBatchResult(this.files, this.manifest.getMissingIds()));
            return;
        }
        var file = this.manifest.getFiles().get(index);
        if (this.httpEndpoint != null) {
            receiveHttp(index, file);
            return;
        }
        var mode = file.size() <= TransferFileReceiver.INLINE_LIMIT ? TransferMode.INLINE : TransferMode.RANGED;
        try {
            // Server paths are never accepted: this is a content-addressed local cache.
            var task = this.manager.register(file.taskId(), this.directory.resolve(file.sha1()), file.size(),
                    file.sha1(), mode, this.timeout);
            this.active = task;
            task.completion().whenComplete((path, error) -> execute(() -> {
                if (this.stopping || this.future.isDone()) return;
                if (error != null) {
                    fail(error);
                    return;
                }
                try {
                    this.active = null;
                    this.committedBytes += file.size();
                    this.files.put(file.resourceId(), path);
                    report();
                    receive(index + 1);
                } catch (Throwable failure) {
                    fail(failure);
                }
            }));
            if (mode == TransferMode.INLINE) {
                this.worker.writeAndFlush(new P71_FTPInlineRequest(file.taskId())).addListener(sent -> {
                    if (!sent.isSuccess()) disconnected(sent.cause());
                });
            } else {
                this.manager.requestRanges(file.taskId(), this.worker, 4).whenComplete((ignored, error) -> {
                    if (error != null) execute(() -> {
                        if (!this.stopping && task.getState() != TransferTask.State.COMPLETED) fail(error);
                    });
                });
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void receiveHttp(int index, P63_RTPBatchResponse.File file) {
        this.httpBytes = 0;
        this.httpActivity = System.nanoTime();
        this.httpStaging = this.directory.resolve(".http-" + this.batchId + "-" + index);
        var staging = this.httpStaging;
        this.httpTask = this.httpExecutor.submit(() -> {
            try {
                this.downloader.download(this.httpEndpoint + file.sha1(), staging, (downloaded, total) -> {
                    if (this.stopping || Thread.currentThread().isInterrupted()) throw new CancellationException("HTTP transfer cancelled");
                    if (total != file.size() || downloaded > file.size()) throw new IllegalArgumentException("HTTP resource length mismatch");
                    this.httpBytes = downloaded;
                    this.httpActivity = System.nanoTime();
                });
                if (java.nio.file.Files.size(staging) != file.size()
                        || !java.util.HexFormat.of().formatHex(FileDigest.calculateSHA1(staging.toFile())).equals(file.sha1())) {
                    throw new java.io.IOException("HTTP resource length or SHA-1 mismatch: " + file.resourceId());
                }
                execute(() -> {
                    if (this.stopping || this.future.isDone()) return;
                    try {
                        var target = this.directory.resolve(file.sha1());
                        java.nio.file.Files.move(staging, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        this.httpStaging = null;
                        this.httpTask = null;
                        this.httpBytes = 0;
                        this.committedBytes += file.size();
                        this.files.put(file.resourceId(), target);
                        report();
                        receive(index + 1);
                    } catch (Throwable error) {
                        fail(error);
                    }
                });
            } catch (Throwable error) {
                execute(() -> fail(error));
            }
        });
    }

    private void closeHttp() {
        if (this.downloader != null) this.downloader.close();
        if (this.httpExecutor != null) this.httpExecutor.shutdown();
    }

    private void cleanupHttp(Throwable error) {
        if (this.httpTask != null) this.httpTask.cancel(true);
        this.downloader.close();
        // Queued after the download: staging cleanup must wait until all download workers stop writing.
        this.httpExecutor.execute(() -> {
            try {
                if (this.httpStaging != null) {
                    for (var suffix : List.of("", ".part", ".resume", ".resume.tmp")) {
                        java.nio.file.Files.deleteIfExists(this.httpStaging.resolveSibling(this.httpStaging.getFileName() + suffix));
                    }
                }
            } catch (Throwable cleanupError) {
                error.addSuppressed(cleanupError);
            } finally {
                this.httpExecutor.shutdown();
                execute(() -> finishFailure(error));
            }
        });
    }

    void disconnected(Throwable error) { execute(() -> fail(error)); }

    private void execute(Runnable action) {
        try {
            this.executor.execute(action);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Late network/disk callbacks after the batch has terminated.
        }
    }

    private void report() {
        this.progress.accept(new TransferProgress(this.committedBytes
                + (this.active == null ? this.httpBytes : this.active.getReceivedBytes()), this.totalBytes,
                this.files.size(), this.manifest.getFiles().size(), this.manifest.getMissingIds()));
    }

    private void fail(Throwable error) {
        if (this.stopping) return;
        this.stopping = true;
        stopTimers();
        if (this.worker != null) this.worker.close();
        this.channel.writeAndFlush(new P64_RTPBatchRelease(this.batchId));
        if (this.httpExecutor != null) {
            cleanupHttp(error);
        } else if (this.active != null) {
            var task = this.active;
            this.manager.cancel(task.getTaskId());
            task.completion().whenComplete((ignored, failure) -> finishFailure(error));
        } else {
            finishFailure(error);
        }
    }

    private void finishFailure(Throwable error) {
        this.executor.shutdown();
        this.termination.complete(null);
        this.future.completeExceptionally(error);
    }

    private void stopTimers() {
        if (this.ticker != null) this.ticker.cancel(false);
        if (this.manifestTimeout != null) this.manifestTimeout.cancel(false);
    }
}
