package me.gb2022.mcupdater;

import io.netty.buffer.ByteBuf;
import me.gb2022.mcupdater.data.VersionAsset;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.CompiledVersionData;

import java.util.*;

public final class UpdateHeader {
    private final Set<CompiledVersionData> versions = new HashSet<>();
    private final Map<String, VersionAsset> assets;
    private final Set<String> extract;
    private final Map<String, String> update;
    private final Set<String> delete;

    public UpdateHeader(ByteBuf buffer) {
        var len = buffer.readInt();
        for (var i = 0; i < len; i++) {
            this.versions.add(new CompiledVersionData(buffer));
        }

        int assetCount = readCount(buffer, Integer.BYTES * 2);
        var assets = new LinkedHashMap<String, VersionAsset>();
        for (int i = 0; i < assetCount; i++) {
            var asset = VersionAsset.read(buffer);
            var key = asset.getResourceKey();
            if (assets.putIfAbsent(key, asset) != null) {
                throw new IllegalArgumentException("Duplicate asset key: " + key);
            }
        }
        this.assets = Map.copyOf(assets);
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

    public void write(ByteBuf buffer) {
        buffer.writeInt(this.versions.size());
        this.versions.forEach(version -> version.write(buffer));

        buffer.writeInt(this.assets.size());
        this.assets.values().forEach(asset -> asset.write(buffer));
        writeStrings(buffer, this.extract);
        buffer.writeInt(this.update.size());
        this.update.forEach((path, sha1) -> {
            BufferUtil.writeString(buffer, path);
            BufferUtil.writeString(buffer, sha1);
        });
        writeStrings(buffer, this.delete);
    }

    public static int readCount(ByteBuf buffer, int minimumEntryBytes) {
        int count = buffer.readInt();
        if (count < 0 || count > buffer.readableBytes() / minimumEntryBytes) {
            throw new IllegalArgumentException("Invalid version collection size: " + count);
        }
        return count;
    }

    public static Set<String> readStrings(ByteBuf buffer) {
        int count = readCount(buffer, Integer.BYTES);
        var strings = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            strings.add(BufferUtil.readString(buffer));
        }
        return Set.copyOf(strings);
    }

    public static void writeStrings(ByteBuf buffer, Collection<String> strings) {
        buffer.writeInt(strings.size());
        strings.forEach(value -> BufferUtil.writeString(buffer, value));
    }


    public Set<String> getDelete() {
        return delete;
    }

    public Set<String> getExtract() {
        return extract;
    }

    public Map<String, String> getUpdate() {
        return update;
    }

    public Map<String, VersionAsset> getAssets() {
        return assets;
    }

    public Set<CompiledVersionData> getVersions() {
        return versions;
    }
}
