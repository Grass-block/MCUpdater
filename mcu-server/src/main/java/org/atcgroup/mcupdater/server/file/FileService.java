package org.atcgroup.mcupdater.server.file;

import org.atcgroup.mcupdater.server.MCUpdaterServer;

import java.util.HashMap;
import java.util.Map;

import static org.atcraftmc.updater.server.MCUpdaterServer.LOGGER;

public final class FileService {
    private final Map<String, FileSource> sources = new HashMap<>();

    public void start() {
        var config = MCUpdaterServer.instance().config().getConfigurationSection("channels");
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
