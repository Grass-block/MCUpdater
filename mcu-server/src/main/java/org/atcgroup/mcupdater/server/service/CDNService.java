package org.atcgroup.mcupdater.server.service;

import org.atcgroup.mcupdater.server.MCUpdaterServer;
import org.atcgroup.mcupdater.server.cdn.CDNHandler;
import org.atcgroup.mcupdater.server.cdn.CDNStorageHandler;
import org.atcgroup.mcupdater.server.cdn.S3StorageHandler;
import org.atcgroup.mcupdater.util.I18n;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;

import static org.atcgroup.mcupdater.server.MCUpdaterServer.LOGGER;

public final class CDNService implements Service {
    private final Set<CDNHandler> handlers = new HashSet<>();

    @Override
    public void handleServerBootstrap(MCUpdaterServer server, ConfigurationSection config) {
        var vs = server.getServiceManager().getService(VersionService.class);

        if (config.getBoolean("cdn-service.s3-cdn.enable")) {
            var h = new S3StorageHandler(config.getConfigurationSection("cdn-service"));
            vs.getCompiledVersionHandlerPipeline().addLast("mcu:s3-cdn", h);
            this.handlers.add(h);
            LOGGER.info("Using S3 cdn service({}@{}).", h.getBucket(), h.getEndpoint());
        }

        if (config.getBoolean("cdn-service.mcu-cdn.enable")) {
            var h = new CDNStorageHandler();
            vs.getCompiledVersionHandlerPipeline().addLast("mcu:mcu-cdn", h);
            this.handlers.add(h);
            LOGGER.info("Using custom cdn service.");
        }
    }

    @Override
    public boolean handleCommand(String command, String[] args) {
        if (command.equals("cdn-upload")) {
            LOGGER.info(I18n.message("console.cdn_upload"));
            this.handlers.forEach((h) -> h.sync(MCUpdaterServer.instance().getPacksFolder()));
            return true;
        }

        return false;
    }
}
