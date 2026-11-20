package org.atcgroup.mcupdater.client.download;

import me.gb2022.commons.math.SHA;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.client.ClientError;
import org.atcgroup.mcupdater.client.ClientFilePath;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.network.RangeDownloader;
import org.atcgroup.mcupdater.client.ui.component.ExtraTaskCard;
import org.atcgroup.mcupdater.data.HttpDownloadInfo;
import org.atcgroup.mcupdater.util.FileDigest;

import java.util.concurrent.atomic.AtomicReference;

public final class ExternDownloadResolver implements DownloadResolver {
    public static final RangeDownloader DOWNLOADER = new RangeDownloader(12, 96, 8L * 1024 * 1024);

    @Override
    public void resolve(UpdateHeader header, DownloadResult result) {
        for (var download : header.getExternDownloads()) {
            result.addPendingTask(MCUpdaterClient.BACKGROUND_EXEC.submit(() -> {
                try {
                    download(download);
                } catch (Exception e) {
                    var ee = new RuntimeException("Failed to download file(ERROR): " + download.getUrl(), e);
                    MCUpdaterClient.instance().handleException(ClientError.NETWORK, ee);
                }
                result.pendingTaskComplete();
            }));
        }
    }

    public void download(HttpDownloadInfo info) throws Exception {
        var client = MCUpdaterClient.instance();
        var task = new AtomicReference<ExtraTaskCard>();
        var file = ClientFilePath.CACHE.append(info.getDest()).file();

        if (file.exists()) {
            var sha1 = SHA.byteArrayToHexString(FileDigest.calculateSHA1(file));

            if (sha1.equals(info.getSha1())) {
                return;
            }
        }

        DOWNLOADER.download(
                info.getUrl(),
                file.toPath(),
                (downloaded, total) -> {
                    if (task.get() == null) {
                        task.set(client.getProcessScreen().addTrackingTask());
                    }

                    task.get().setWork(downloaded);
                    task.get().setProgressTitle("正在下载文件: %s (%s/%sKiB)".formatted(info.getDest(), downloaded / 1024, total / 1024));
                    task.get().setProgress((int) ((float) downloaded / total * 100));
                }
        );

        if (!SHA.byteArrayToHexString(FileDigest.calculateSHA1(file)).equals(info.getSha1())) {
            download(info);
        }

        task.get().close();
    }
}
