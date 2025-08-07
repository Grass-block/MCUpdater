package org.atcraftmc.updater.server.file;

import org.atcraftmc.updater.data.UpdateChannelMeta;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class FileSource {
    private final UpdateChannelMeta meta;
    private final FileStatusManager statusManager;
    private final String path;

    public FileSource(String id, ConfigurationSection config) {
        this.path = config.getString("path");

        var name = config.getString("name");
        var desc = config.getString("desc");
        var required = config.getBoolean("required", false);

        this.meta = new UpdateChannelMeta(id, name, desc, required);
        this.statusManager = new FileStatusManager(id, config);
    }

    public String path() {
        return path;
    }

    public UpdateChannelMeta meta() {
        return meta;
    }

    public FileStatusManager fileManager() {
        return statusManager;
    }

    public Set<String> paths() {
        var paths = new HashSet<String>();
        this.statusManager.iterate((p, f) -> paths.add(p));
        return paths;
    }

    public File file(String path){
        return new File(this.path + path);
    }

    public Set<File> files() {
        return paths().stream().map(this::file).collect(Collectors.toSet());
    }
}
