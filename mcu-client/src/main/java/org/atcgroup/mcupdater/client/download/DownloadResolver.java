package org.atcgroup.mcupdater.client.download;

import org.atcgroup.mcupdater.network.packet.P21_VersionHeaders;

@FunctionalInterface
public interface DownloadResolver {
    DownloadResolver[] RESOLVERS = {
            new ServerDownloadResolver(),
            new ExternDownloadResolver(),
    };

    void resolve(P21_VersionHeaders headers, DownloadResult result);
}
