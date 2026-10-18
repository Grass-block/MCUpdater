package org.atcgroup.mcupdater.server.service;

import me.gb2022.simpnet.channel.NettyChannelInitializer;
import org.atcgroup.mcupdater.network.handler.FileServerHandler;
import org.atcgroup.mcupdater.server.MCUpdaterServer;

public final class ResourcePackService implements Service {
    @Override
    public void handleNetworkBootstrap(MCUpdaterServer server, NettyChannelInitializer initializer) {
        initializer.handler(() -> new FileServerHandler(server.getPacksFolder(), server.config().getInt("http-server-port"),"default"));
    }
}
