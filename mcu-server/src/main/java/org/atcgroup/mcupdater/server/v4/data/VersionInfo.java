package org.atcgroup.mcupdater.server.v4.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A version manifest describing local resources and the filesystem changes to apply. */
public final class VersionInfo {
    private final String channel;
    private final String version;
    private final long timestamp;
    private final VersionAssets assets;
    private final List<String> extract;
    private final Map<String, String> update;
    private final List<String> delete;

    public VersionInfo(String channel, String version, long timestamp, VersionAssets assets,
                       List<String> extract, Map<String, String> update, List<String> delete) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.version = Objects.requireNonNull(version, "version");
        this.timestamp = timestamp;
        this.assets = Objects.requireNonNull(assets, "assets");
        this.extract = List.copyOf(extract);
        this.update = Map.copyOf(update);
        this.delete = List.copyOf(delete);
    }

    public VersionInfo(ByteBuf buffer) {
        this.channel = BufferUtil.readString(buffer);
        this.version = BufferUtil.readString(buffer);
        this.timestamp = buffer.readLong();
        this.assets = new VersionAssets(buffer);
        this.extract = readStrings(buffer);
        int size = readCount(buffer, Integer.BYTES * 2);
        var updates = new LinkedHashMap<String, String>();
        for (int i = 0; i < size; i++) {
            var path = BufferUtil.readString(buffer);
            var sha1 = BufferUtil.readString(buffer);
            if (updates.putIfAbsent(path, sha1) != null) {
                throw new IllegalArgumentException("Duplicate update path: " + path);
            }
        }
        this.update = Map.copyOf(updates);
        this.delete = readStrings(buffer);
    }

    /** Strings use SimpNet encoding; collection counts are ints, timestamp and lengths are longs. */
    public void encode(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.channel);
        BufferUtil.writeString(buffer, this.version);
        buffer.writeLong(this.timestamp);
        this.assets.encode(buffer);
        writeStrings(buffer, this.extract);
        buffer.writeInt(this.update.size());
        this.update.forEach((path, sha1) -> {
            BufferUtil.writeString(buffer, path);
            BufferUtil.writeString(buffer, sha1);
        });
        writeStrings(buffer, this.delete);
    }

    static int readCount(ByteBuf buffer, int minimumEntryBytes) {
        int count = buffer.readInt();
        if (count < 0 || count > buffer.readableBytes() / minimumEntryBytes) {
            throw new IllegalArgumentException("Invalid version collection size: " + count);
        }
        return count;
    }

    static List<String> readStrings(ByteBuf buffer) {
        int count = readCount(buffer, Integer.BYTES);
        var strings = new ArrayList<String>();
        for (int i = 0; i < count; i++) strings.add(BufferUtil.readString(buffer));
        return List.copyOf(strings);
    }

    static void writeStrings(ByteBuf buffer, List<String> strings) {
        buffer.writeInt(strings.size());
        strings.forEach(value -> BufferUtil.writeString(buffer, value));
    }

    public static VersionInfo fromJson(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    public static VersionInfo fromJson(JsonObject json) {
        var assetJson = json.getAsJsonObject("assets");
        var server = new ArrayList<String>();
        if (assetJson.has("server")) {
            for (var item : assetJson.getAsJsonArray("server")) server.add(item.getAsString());
        }

        var downloads = new LinkedHashMap<String, DownloadAsset>();
        if (assetJson.has("download")) {
            for (var entry : assetJson.getAsJsonObject("download").entrySet()) {
                var download = entry.getValue().getAsJsonObject();
                downloads.put(entry.getKey(), new DownloadAsset(download.get("url").getAsString(),
                        download.get("length").getAsLong()));
            }
        }

        var extract = strings(json, "extract");
        var delete = strings(json, "delete");
        var update = new LinkedHashMap<String, String>();
        if (json.has("update")) {
            for (var entry : json.getAsJsonObject("update").entrySet()) {
                update.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return new VersionInfo(json.get("channel").getAsString(), json.get("version").getAsString(),
                json.get("timestamp").getAsLong(), new VersionAssets(server, downloads), extract, update, delete);
    }

    private static List<String> strings(JsonObject json, String key) {
        var result = new ArrayList<String>();
        if (json.has(key)) {
            for (var item : json.getAsJsonArray(key)) result.add(item.getAsString());
        }
        return result;
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("channel", this.channel);
        json.addProperty("version", this.version);
        json.addProperty("timestamp", this.timestamp);
        json.add("assets", this.assets.toJson());
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

    public String getChannel() { return channel; }
    public String getVersion() { return version; }
    public long getTimestamp() { return timestamp; }
    public VersionAssets getAssets() { return assets; }
    public List<String> getExtract() { return extract; }
    public Map<String, String> getUpdate() { return update; }
    public List<String> getDelete() { return delete; }
}
