package org.atcraftmc.updater.server.service;

import org.atcgroup.mcupdater.server.file.FileSource;
import org.atcraftmc.updater.server.MCUpdaterServer;

import java.util.HashMap;
import java.util.Map;

import static org.atcraftmc.updater.server.MCUpdaterServer.LOGGER;

public final class FileService extends Service {
    private final Map<String, FileSource> sources = new HashMap<>();

    public FileService(MCUpdaterServer server) {
        super(server);
    }

    @Override
    public String name() {
        return "file-service";
    }

    @Override
    public boolean async() {
        return false;
    }

    @Override
    public void run() {
        var config = server().config().getConfigurationSection("channels");
        var ids = config.getKeys(false);
        LOGGER.info("Loaded {} update channels:", ids.size());

        for (var s : ids) {
            var source = new FileSource(s, config.getConfigurationSection(s));
            var meta = source.meta();

            this.sources.put(s, source);

            LOGGER.info(" - {}({}) [Enforce: {}] -> {}", meta.id(), meta.name(), meta.required(), source.path());
        }
    }

    public Map<String, FileSource> sources() {
        return sources;
    }
}
