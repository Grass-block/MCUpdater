package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P40_CDNFileCheckRequest implements Packet {
    private final String repo;
    private final Map<String, byte[]> hashes = new HashMap<>();

    public P40_CDNFileCheckRequest(String repo) {
        super();
        this.repo = repo;
    }

    @DeserializedConstructor
    public P40_CDNFileCheckRequest(ByteBuf buffer) {
        this.repo = BufferUtil.readString(buffer);
        var size = buffer.readShort();
        for (int i = 0; i < size; i++) {
            var name = BufferUtil.readString(buffer);
            var hash = BufferUtil.readArray(buffer);

            addHashInfo(name, hash);
        }
    }

    public void addHashInfo(String file, byte[] hash) {
        this.hashes.put(file, hash);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.repo);
        buffer.writeShort(this.hashes.size());

        for (Map.Entry<String, byte[]> entry : this.hashes.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            BufferUtil.writeArray(buffer, entry.getValue());
        }
    }

    public Map<String, byte[]> getHashes() {
        return hashes;
    }

    public String getRepo() {
        return repo;
    }
}
