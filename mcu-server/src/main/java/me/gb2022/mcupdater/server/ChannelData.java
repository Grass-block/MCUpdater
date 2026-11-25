package me.gb2022.mcupdater.server;

import me.gb2022.mcupdater.data.ChannelMetadata;
import me.gb2022.mcupdater.data.TranslatableComponent;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChannelData {
    private final ChannelMetadata meta;
    private final String path;
    private final List<String> rejectFileList;
    private final List<String> addFileList;
    private final List<String> blockFileList;

    public ChannelData(ConfigurationSection config) {
        var id = config.getString("id");
        var enforced = config.getBoolean("enforced");
        var name = readTranslations(config, "name");
        var desc = readTranslations(config, "desc");

        this.meta = new ChannelMetadata(id, enforced, name, desc);
        this.path = config.getString("path");
        this.rejectFileList = config.getStringList("reject-files");
        this.addFileList = config.getStringList("add-files");
        this.blockFileList = config.getStringList("block-files");
    }

    private static TranslatableComponent readTranslations(ConfigurationSection config, String key) {
        var section = config.getConfigurationSection(key);
        if (section == null || section.getKeys(false).isEmpty()) {
            // Keep scalar text usable and allow an omitted description.
            return new TranslatableComponent(Map.of("zh_cn", section == null ? config.getString(key, "") : ""));
        }
        var translations = new LinkedHashMap<String, String>();
        for (var language : section.getKeys(false)) {
            translations.put(language, section.getString(language, ""));
        }
        return new TranslatableComponent(translations);
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
