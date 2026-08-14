package org.atcgroup.mcupdater.server.service;

import me.gb2022.commons.file.FilePath;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import org.atcgroup.mcupdater.network.handler.FileServerHandler;
import org.atcgroup.mcupdater.util.FileChecksumManager;

public final class ResourcePackService implements Service {
    private final FileChecksumManager checksumManager = new FileChecksumManager(FilePath.RUNTIME.append("packs"));

    @Override
    public void handleNetworkBootstrap(NettyChannelInitializer initializer) {
        initializer.handler(() -> new FileServerHandler(this.checksumManager));
    }

    public FileChecksumManager getChecksumManager() {
        return checksumManager;
    }
}
