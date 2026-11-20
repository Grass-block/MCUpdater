package org.atcgroup.mcupdater.server.v4.data;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

public final class ChannelData {
    private final ChannelMetadata meta;
    private final String path;
    private final List<String> rejectFileList;
    private final List<String> addFileList;
    private final List<String> blockFileList;

    @SuppressWarnings("unchecked")
    public ChannelData(ConfigurationSection config) {
        var id = config.getString("id");
        var enforced = config.getBoolean("enforced");
        var name = new TranslatableComponent((Map<String, String>) config.get("name"));
        var desc = new TranslatableComponent((Map<String, String>) config.get("desc"));

        this.meta = new ChannelMetadata(id, enforced, name, desc);
        this.path = config.getString("path");
        this.rejectFileList = config.getStringList("reject-files");
        this.addFileList = config.getStringList("add-files");
        this.blockFileList = config.getStringList("block-files");
    }

    public ChannelMetadata getMeta() {
        return meta;
    }

    public List<String> getAddFileList() {
        return addFileList;
    }

    public List<String> getBlockFileList() {
        return blockFileList;
    }

    public List<String> getRejectFileList() {
        return rejectFileList;
    }

    public String getPath() {
        return path;
    }
}
