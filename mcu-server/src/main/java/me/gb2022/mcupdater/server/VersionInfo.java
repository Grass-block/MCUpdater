package me.gb2022.mcupdater.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.gb2022.mcupdater.data.VersionAsset;

import java.util.*;

/**
 * A version manifest describing local resources and the filesystem changes to apply.
 */
public final class VersionInfo {
    private final String channel;
    private final String version;
    private final long timestamp;
    private final Map<String, VersionAsset> assets;
    private final List<String> extract;
    private final Map<String, String> update;
    private final List<String> delete;

    public VersionInfo(String channel, String version, long timestamp, Map<String, VersionAsset> assets, List<String> extract, Map<String, String> update, List<String> delete) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.version = Objects.requireNonNull(version, "version");
        this.timestamp = timestamp;
        this.assets = Map.copyOf(assets);
        this.assets.forEach((key, asset) -> {
            if (!key.equals(asset.getResourceKey())) {
                throw new IllegalArgumentException("Asset key does not match resource key: " + key);
            }
        });
        this.extract = List.copyOf(extract);
        this.update = Map.copyOf(update);
        this.delete = List.copyOf(delete);
    }

    public static VersionInfo fromJson(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    public static VersionInfo fromJson(JsonObject json) {
        var assetJson = json.getAsJsonObject("assets");
        var assets = new LinkedHashMap<String, VersionAsset>();
        for (var entry : assetJson.entrySet()) {
            assets.put(entry.getKey(), VersionAsset.read(entry.getKey(), entry.getValue().getAsJsonObject()));
        }

        var extract = strings(json, "extract");
        var delete = strings(json, "delete");
        var update = new LinkedHashMap<String, String>();
        if (json.has("update")) {
            for (var entry : json.getAsJsonObject("update").entrySet()) {
                update.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return new VersionInfo(json.get("channel").getAsString(), json.get("version")
                                                                      .getAsString(), json.get("timestamp")
                                                                                          .getAsLong(), assets, extract, update, delete);
    }

    private static List<String> strings(JsonObject json, String key) {
        var result = new ArrayList<String>();
        if (json.has(key)) {
            for (var item : json.getAsJsonArray(key)) {
                result.add(item.getAsString());
            }
        }
        return result;
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("channel", this.channel);
        json.addProperty("version", this.version);
        json.addProperty("timestamp", this.timestamp);
        var assetJson = new JsonObject();
        this.assets.forEach((key, asset) -> {
            var resource = new JsonObject();
            asset.write(resource);
            assetJson.add(key, resource);
        });
        json.add("assets", assetJson);
        json.add("extract", array(this.extract));
        var updateJson = new JsonObject();
        this.update.forEach(updateJson::addProperty);
        json.add("update", updateJson);
        json.add("delete", array(this.delete));
        return json;
    }

    private static JsonArray array(List<String> items) {
        var result = new JsonArray();
        items.forEach(result::add);
        return result;
    }

    public String getChannel() {
        return channel;
    }

    public String getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, VersionAsset> getAssets() {
        return assets;
    }

    public List<String> getExtract() {
        return extract;
    }

    public Map<String, String> getUpdate() {
        return update;
    }

    public List<String> getDelete() {
        return delete;
    }
}
