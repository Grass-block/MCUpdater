package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P11_UpdateRequest implements Packet {
    private final Map<String,Long> currentVersions;

    public P11_UpdateRequest(Map<String,Long> currentVersions) {
        this.currentVersions = currentVersions;
    }

    @DeserializedConstructor
    public P11_UpdateRequest(ByteBuf buffer) {
        this.currentVersions = new HashMap<>();

        var len = buffer.readInt();

        for (int i = 0; i < len; i++) {
            this.currentVersions.put(BufferUtil.readString(buffer), buffer.readLong());
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.currentVersions.size());

        for (Map.Entry<String,Long> entry : this.currentVersions.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            buffer.writeLong(entry.getValue());
        }
    }

    public Map<String, Long> getCurrentVersions() {
        return currentVersions;
    }
}
