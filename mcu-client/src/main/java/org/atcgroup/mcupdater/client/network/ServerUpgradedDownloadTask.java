package org.atcgroup.mcupdater.client.network;

import me.gb2022.commons.math.SHA;
import me.gb2022.mcupdater.client.ui.desktop.UI;
import me.gb2022.mcupdater.client.util.ClientFilePath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.network.RangeDownloader;
import org.atcgroup.mcupdater.network.packet.P33_FTPUpgradeToHttp;
import org.atcgroup.mcupdater.util.FileDigest;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class ServerUpgradedDownloadTask implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger(ServerUpgradedDownloadTask.class);

    private final RangeDownloader downloader = new RangeDownloader(12, 96, 8L * 1024 * 1024);
    private final P33_FTPUpgradeToHttp message;
    private final String serverHost;

    public ServerUpgradedDownloadTask(P33_FTPUpgradeToHttp info, String serverHost) {
        this.message = info;
        this.serverHost = serverHost;
    }

    public static CompletableFuture<?> start(P33_FTPUpgradeToHttp message, String hostName) {
        LOGGER.info("Redirected to http download: {}:{}", hostName, message.getRemoteServicePort());
        return CompletableFuture.runAsync(new ServerUpgradedDownloadTask(message, hostName));
    }

    @Override
    public void run() {
        for (var file : message.getFileToDownload().entrySet()) {
            LOGGER.info("start: {}",file.getKey());
            download(file.getKey(), file.getValue());
            LOGGER.info("end: {}",file.getKey());
        }
    }

    public void download(String resId, String hash) {
        var port = this.message.getRemoteServicePort();
        var repo = this.message.getRemoteServerRepo();
        var url = "http://" + this.serverHost + ":" + port + "/" + repo + "/" + resId;
        var file = ClientFilePath.CACHE.append(resId).file();

        try {
            this.downloader.download(url, file.toPath(), (downloaded, total) -> {
                var r = UI.NUMBER_FORMAT.format(downloaded / 1024f / 1024f);
                var t = UI.NUMBER_FORMAT.format(total / 1024f / 1024f);
                var p = (int) ((double) downloaded / (double) total * 100);

                MCUpdaterClient.instance().getProcessScreen().setProgress(p);
                MCUpdaterClient.instance().getProcessScreen().setProgressTitle("正在下载资源文件: %s - %s%% (%s/%s MiB)".formatted(resId, p, r, t));
            });

            var sha1 = SHA.byteArrayToHexString(FileDigest.calculateSHA1(file));

            if (!sha1.equals(sha1)) {
                download(resId, hash);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
