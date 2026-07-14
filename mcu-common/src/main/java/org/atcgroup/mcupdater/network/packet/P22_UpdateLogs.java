package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P22_UpdateLogs implements Packet {
    private final Map<String, String> logs;

    public P22_UpdateLogs(Map<String, String> logs) {
        this.logs = logs;
    }

    @DeserializedConstructor
    public P22_UpdateLogs(ByteBuf buffer) {
        this.logs = new HashMap<>();

        var length = buffer.readInt();

        for (var i = 0; i < length; i++) {
            this.logs.put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.logs.size());

        for (var entry : this.logs.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            BufferUtil.writeString(buffer, entry.getValue());
        }
    }
}
