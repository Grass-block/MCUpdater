package org.atcgroup.mcupdater.client.download;

import org.atcgroup.mcupdater.UpdateHeader;

@FunctionalInterface
public interface DownloadResolver {
    DownloadResolver[] RESOLVERS = {
            new ServerDownloadResolver(),
            new ExternDownloadResolver(),
    };

    void resolve(UpdateHeader header, DownloadResult result);
}
