package org.atcgroup.mcupdater.client.download;

import org.atcgroup.mcupdater.UpdateHeader;
import me.gb2022.mcupdater.client.ClientError;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.client.network.CDNClient;
import org.atcgroup.mcupdater.network.packet.P30_FileDownloadRequest;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class ServerDownloadResolver implements DownloadResolver {

    @Override
    public void resolve(UpdateHeader header, DownloadResult result) {
        var executor = Executors.newSingleThreadExecutor();
        result.addPendingTask(executor.submit(() -> downloadFile(header, result)));
        executor.shutdown();
    }

    public void downloadFile(UpdateHeader header, DownloadResult result) {
        var client = MCUpdaterClient.instance();
        var meta = client.getServerMeta();
        var task = client.getProcessScreen();
        var network = client.getNetworkService();

        task.setActive(true);

        if (!meta.hasCDNInfo()) {
            task.setUnsureProgress("正在初始化下载进程...");
            network.write(new P30_FileDownloadRequest(header.getServerDownloads()));
            return;
        }

        var addr = new InetSocketAddress(meta.getCdnHost(), meta.getCdnPort());

        new CDNClient(client, addr, meta.getCdnRepository(), header, (f) -> {
            if (f == null) {
                client.handleException(ClientError.OTHER, new NullPointerException());
            }
            task.setUnsureProgress("正在初始化下载进程...");
            network.write(new P30_FileDownloadRequest(f));
        }).run();
    }
}
