package me.gb2022.mcupdater.server.diff;

import me.gb2022.mcupdater.data.ChannelMetadata;
import me.gb2022.mcupdater.data.TranslatableComponent;
import org.atcgroup.mcupdater.server.file.FileStatusManager;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class FileSource {
    private final ChannelMetadata meta;
    private final FileStatusManager statusManager;
    private final String path;

    public FileSource(String id, ConfigurationSection config) {
        this.path = config.getString("path");

        var name = new TranslatableComponent(config.getConfigurationSection("name").getValues(false));
        var desc = new TranslatableComponent(config.getConfigurationSection("desc").getValues(false));
        var required = config.getBoolean("required", false);

        this.meta = new ChannelMetadata(id, required, name, desc);
        this.statusManager = new FileStatusManager(id, config);
    }

    public String path() {
        return path;
    }

    public ChannelMetadata meta() {
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
