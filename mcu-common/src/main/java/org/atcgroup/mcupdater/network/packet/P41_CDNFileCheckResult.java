package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class P41_CDNFileCheckResult implements Packet {
    private final Set<String> needUpdate = new HashSet<>();

    public P41_CDNFileCheckResult() {
        super();
    }

    @DeserializedConstructor
    public P41_CDNFileCheckResult(ByteBuf buffer) {
        var size = buffer.readShort();
        for (int i = 0; i < size; i++) {
            addFile(BufferUtil.readString(buffer));
        }
    }

    public void addFile(String file) {
        this.needUpdate.add(file);
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeShort(this.needUpdate.size());

        for (var f: this.needUpdate) {
            BufferUtil.writeString(buffer, f);
        }
    }

    public Set<String> getNeedUpdate() {
        return needUpdate;
    }
}
