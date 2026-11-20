package org.atcgroup.mcupdater.server.v4.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.server.v4.data.ChannelMetadata;

import java.util.HashMap;
import java.util.Map;

public final class P20_ChannelMetaList implements Packet {
    private final Map<String, ChannelMetadata> channels;

    public P20_ChannelMetaList() {
        this.channels = new HashMap<>();
    }

    @DeserializedConstructor
    public P20_ChannelMetaList(ByteBuf buffer) {
        this();
        var size = buffer.readByte();

        for (int i = 0; i < size; i++) {
            var key = BufferUtil.readString(buffer);
            var data = new ChannelMetadata(buffer);

            this.channels.put(key, data);
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeByte(this.channels.size());
        for (var entry : this.channels.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            entry.getValue().encode(buffer);
        }
    }
}
