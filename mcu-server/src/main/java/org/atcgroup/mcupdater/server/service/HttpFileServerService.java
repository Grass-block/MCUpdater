package org.atcgroup.mcupdater.server.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.NettyHttpFileServer;
import org.atcgroup.mcupdater.server.MCUpdaterServer;
import org.bukkit.configuration.ConfigurationSection;

public final class HttpFileServerService implements Service {
    private static final Logger LOGGER = LogManager.getLogger("HttpFileService");
    private final NettyHttpFileServer server = new NettyHttpFileServer();

    @Override
    public void handleServerBootstrap(MCUpdaterServer server, ConfigurationSection config) throws Throwable {
        if (server.config().getInt("http-server-port") == -1) {
            return;
        }

        this.server.start(config.getInt("http-server-port"));
        this.server.registerBucket("default", server.getPacksFolder());
        LOGGER.info("Started HTTP file transport on {}", config.getInt("http-server-port"));
    }

    @Override
    public void handleServerClose(MCUpdaterServer server) {
        if (server.config().getInt("http-server-port") != -1) {
            this.server.close();
        }
    }
}
