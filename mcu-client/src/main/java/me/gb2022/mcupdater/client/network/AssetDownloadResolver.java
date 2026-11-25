package me.gb2022.mcupdater.client.network;

import me.gb2022.commons.math.SHA;
import me.gb2022.mcupdater.client.MCUpdaterClient;
import me.gb2022.mcupdater.client.util.ClientFilePath;
import me.gb2022.mcupdater.client.util.TaskBatch;
import me.gb2022.mcupdater.data.VersionAsset;
import me.gb2022.mcupdater.rtp.RTPClient;
import me.gb2022.mcupdater.rtp.protocol.P62_RTPBatchRequest;
import org.atcgroup.mcupdater.network.RangeDownloader;
import org.atcgroup.mcupdater.util.FileDigest;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Resolves remote assets into verified local resources before applying an update. */
public interface AssetDownloadResolver {
    AssetDownloadResolver[] ITEMS = {new HttpDownloadResolver(), new RTPDownloadResolver()};

    static void resolveAsset(MCUpdaterClient client, Collection<VersionAsset> assets, TaskBatch batch) {
        var unresolved = new ArrayList<>(assets);
        for (var asset : unresolved) {
            if (asset.getResourceKey() == null || !asset.getResourceKey().matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("Invalid resource SHA-1: " + asset.getResourceKey());
            }
        }
        for (var resolver : AssetDownloadResolver.ITEMS) {
            resolver.resolve(client, unresolved.iterator(), batch);
        }

        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException("Not resolved assets: " + unresolved);
        }
    }

    void resolve(MCUpdaterClient client, Iterator<VersionAsset> assets, TaskBatch processor);

    final class HttpDownloadResolver implements AssetDownloadResolver {
        public static final RangeDownloader DOWNLOADER = new RangeDownloader(12, 96, 8L * 1024 * 1024);

        @Override
        public void resolve(MCUpdaterClient client, Iterator<VersionAsset> assets, TaskBatch processor) {
            while (assets.hasNext()) {
                var asset = assets.next();

                if (!(asset instanceof VersionAsset.HttpAsset h)) {
                    continue;
                }

                processor.submitAsyncTask((t) -> {
                    try {
                        download(h, t);
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        throw new RuntimeException("Failed to download file: " + h.getUrl(), e);
                    }
                });

                assets.remove();
            }
        }

        public void download(VersionAsset.HttpAsset info, TaskBatch.Task context) throws Exception {
            var file = ClientFilePath.CACHE.append(info.getResourceKey()).file();

            if (file.exists()) {
                var sha1 = SHA.byteArrayToHexString(FileDigest.calculateSHA1(file));

                if (file.length() == info.getLength() && sha1.equals(info.getResourceKey())) {
                    return;
                }
            }

            for (int attempt = 0; attempt < 3; attempt++) {
                DOWNLOADER.download(info.getUrl(), file.toPath(), (downloaded, total) -> {
                    var title = "正在下载文件: %s (%s/%sKiB)".formatted(info.getResourceKey(), downloaded / 1024, total / 1024);
                    context.reportProgress(title, downloaded / 1024, total / 1024);
                });
                if (file.length() == info.getLength()
                        && SHA.byteArrayToHexString(FileDigest.calculateSHA1(file)).equals(info.getResourceKey())) {
                    return;
                }
                Files.deleteIfExists(file.toPath());
            }
            throw new IOException("Resource length or SHA-1 mismatch after 3 attempts: " + info.getResourceKey());
        }
    }

    final class RTPDownloadResolver implements AssetDownloadResolver {

        @Override
        public void resolve(MCUpdaterClient client, Iterator<VersionAsset> assets, TaskBatch processor) {
            var generated = new HashSet<String>();
            var missing = new HashSet<String>();

            while (assets.hasNext()) {
                var asset = assets.next();
                if (!(asset instanceof VersionAsset.RTPAsset rtp)) {
                    continue;
                }
                generated.add(rtp.getResourceKey());
                assets.remove();
            }

            if (generated.isEmpty()) return;
            var cdn = client.getServerInfo().hasCDNServer();

            if (cdn) {
                processor.createMainTask((callback) -> {
                    var meta = client.getServerInfo();
                    var address = new InetSocketAddress(meta.getCdnHost(), meta.getCdnPort());
                    try (var c = new RTPClient(ClientFilePath.CACHE);
                         var connection = RTPClient.runStandalone(c, address)) {
                        c.runInit().toCompletableFuture().get();

                        missing.addAll(downloadBatches(c, generated, callback, "正在下载文件(1/2)"));

                    } catch (Exception e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        throw new RuntimeException("CDN resource download failed", e);
                    }
                });
            } else {
                missing.addAll(generated);
            }

            processor.createMainTask((callback) -> {
                if (missing.isEmpty()) return;
                var channel = client.getConnector().channel();
                var pipe = channel.pipeline();
                try (var c = new RTPClient(ClientFilePath.CACHE)) {
                    var handler = c.masterHandler();
                    try {
                        // handlerAdded must run on the EventLoop before runInit reads its Channel.
                        channel.eventLoop().submit(() -> pipe.addLast(handler)).sync();
                        c.runInit().toCompletableFuture().get();
                        var unavailable = downloadBatches(c, missing, callback,
                                cdn ? "正在下载文件(2/2)" : "正在下载文件");
                        if (!unavailable.isEmpty()) {
                            throw new IOException("Resources missing from origin: " + unavailable);
                        }
                    } finally {
                        // Remove on the EventLoop even if this waiting thread was interrupted.
                        channel.eventLoop().submit(() -> {
                            if (pipe.context(handler) != null) pipe.remove(handler);
                        }).syncUninterruptibly();
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new RuntimeException("Origin resource download failed", e);
                }
            });
        }

        private static Set<String> downloadBatches(RTPClient client, Collection<String> resourceIds,
                                                   TaskBatch.Task callback, String title) throws Exception {
            var ids = List.copyOf(resourceIds);
            var missing = new HashSet<String>();
            long receivedBytes = 0;
            long knownTotalBytes = 0;
            boolean multipleBatches = ids.size() > P62_RTPBatchRequest.MAX_FILES;
            // Later manifests have unknown byte totals. Reserve a fixed progress interval for
            // each resource, then interpolate by bytes within each batch to avoid regression.
            final long progressScale = 1_000_000L;
            for (int offset = 0; offset < ids.size();) {
                int end = offset + Math.min(P62_RTPBatchRequest.MAX_FILES, ids.size() - offset);
                int completedResources = offset;
                int batchSize = end - offset;
                long previousBytes = receivedBytes;
                long previousTotal = knownTotalBytes;
                var batchReceived = new AtomicLong();
                var batchTotal = new AtomicLong();
                var result = client.requestFiles(ids.subList(offset, end).toArray(String[]::new), progress -> {
                    batchReceived.set(progress.receivedBytes());
                    batchTotal.set(progress.totalBytes());
                    long received = Math.addExact(previousBytes, progress.receivedBytes());
                    long total = Math.addExact(previousTotal, progress.totalBytes());
                    var message = "%s [%s/%s]".formatted(title, received, total);
                    if (multipleBatches) {
                        double fraction = progress.completed() ? 1.0 : progress.totalBytes() == 0 ? 0.0
                                : Math.min(1.0, (double) progress.receivedBytes() / progress.totalBytes());
                        long work = completedResources * progressScale + (long) (batchSize * progressScale * fraction);
                        callback.reportProgress(message, work, ids.size() * progressScale);
                    } else {
                        callback.reportProgress(message, received, total);
                    }
                }).get();
                missing.addAll(result.missingIds());
                receivedBytes = Math.addExact(receivedBytes, batchReceived.get());
                knownTotalBytes = Math.addExact(knownTotalBytes, batchTotal.get());
                offset = end;
            }
            return missing;
        }
    }
}
