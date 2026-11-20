package org.atcgroup.mcupdater.server.v4.data;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VersionAssets {
    private final List<String> server;
    private final Map<String, DownloadAsset> download;

    public VersionAssets(List<String> server, Map<String, DownloadAsset> download) {
        this.server = List.copyOf(server);
        this.download = Map.copyOf(download);
    }

    public VersionAssets(ByteBuf buffer) {
        this.server = VersionInfo.readStrings(buffer);
        int count = VersionInfo.readCount(buffer, Integer.BYTES * 2 + Long.BYTES);
        var downloads = new LinkedHashMap<String, DownloadAsset>();
        for (int i = 0; i < count; i++) {
            var sha1 = BufferUtil.readString(buffer);
            var asset = new DownloadAsset(buffer);
            if (downloads.putIfAbsent(sha1, asset) != null) {
                throw new IllegalArgumentException("Duplicate download asset: " + sha1);
            }
        }
        this.download = Map.copyOf(downloads);
    }

    public void encode(ByteBuf buffer) {
        VersionInfo.writeStrings(buffer, this.server);
        buffer.writeInt(this.download.size());
        this.download.forEach((sha1, asset) -> {
            BufferUtil.writeString(buffer, sha1);
            asset.encode(buffer);
        });
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        var serverJson = new com.google.gson.JsonArray();
        this.server.forEach(serverJson::add);
        json.add("server", serverJson);
        var downloadJson = new JsonObject();
        this.download.forEach((sha1, asset) -> downloadJson.add(sha1, asset.toJson()));
        json.add("download", downloadJson);
        return json;
    }

    public List<String> getServer() { return server; }
    public Map<String, DownloadAsset> getDownload() { return download; }
}
