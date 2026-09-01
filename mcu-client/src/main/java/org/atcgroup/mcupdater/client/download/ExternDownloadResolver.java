package org.atcgroup.mcupdater.client.download;

import me.gb2022.commons.math.SHA;
import org.atcgroup.mcupdater.client.ClientError;
import org.atcgroup.mcupdater.client.ClientFilePath;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.client.ui.TaskListener;
import org.atcgroup.mcupdater.client.ui.component.ExtraTaskCard;
import org.atcgroup.mcupdater.data.HttpDownloadInfo;
import org.atcgroup.mcupdater.network.packet.P21_VersionHeaders;
import org.atcgroup.mcupdater.util.DiffCheck;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;

public final class ExternDownloadResolver implements DownloadResolver {
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Override
    public void resolve(P21_VersionHeaders headers, DownloadResult result) {
        for (var versionInfo : headers.getVersions()) {
            for (var download : versionInfo.getDownloadFileList()) {
                result.addPendingTask(MCUpdaterClient.BACKGROUND_EXEC.submit(() -> {
                    try {
                        download(download, result);
                    } catch (Exception e) {
                        var ee = new RuntimeException("Failed to download file(ERROR): " + download.getUrl(), e);
                        MCUpdaterClient.instance().handleException(ClientError.NETWORK, ee);
                    }
                }));
            }
        }
    }

    public void download(HttpDownloadInfo info, DownloadResult result) throws Exception {
        var client = MCUpdaterClient.instance();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(info.getUrl()))
                .header("User-Agent", "org.atcgroup.mcupdater.client/4.1.0")
                .GET()
                .build();

        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            client.handleException(
                    ClientError.NETWORK,
                    new RuntimeException("Failed to download file(ERROR-" + response.statusCode() + "): " + info.getUrl())
            );
            return;
        }

        var total = response.headers().firstValueAsLong("Content-Length").orElse(-1);

        if (total != info.getSize()) {
            client.handleException(ClientError.NETWORK, new RuntimeException("Failed to download file(MISMATCH): " + info.getUrl()));
            return;
        }

        var name = UUID.randomUUID().toString();
        var file = ClientFilePath.CACHE.append(name).file();

        Files.createDirectories(file.getParentFile().toPath());
        ExtraTaskCard task = null;
        try (var in = response.body(); var out = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];

            long downloaded = 0;
            int length;

            while ((length = in.read(buffer)) != -1) {

                out.write(buffer, 0, length);

                downloaded += length;

                if (task == null) {
                    task = client.getProcessScreen().addTrackingTask();
                }

                task.setProgressTitle("正在下载文件: %s (%s/%sKiB)".formatted(info.getUrl(), downloaded / 1024, total / 1024));
                task.setProgress((int) ((float) downloaded / total * 100));
            }
        }

        if (!SHA.byteArrayToHexString(DiffCheck.calculateSHA1(file)).equals(info.getSha1())) {
            download(info, result);
        }

        result.addUpdateFile(info.getDest(), name);
        Objects.requireNonNull(task).close();
    }
}
