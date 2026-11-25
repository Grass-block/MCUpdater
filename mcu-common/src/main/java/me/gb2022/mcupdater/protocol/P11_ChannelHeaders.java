package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import me.gb2022.mcupdater.data.ChannelMetadata;

import java.util.HashMap;
import java.util.Map;

public final class P11_ChannelHeaders implements Packet {
    private final Map<String, ChannelMetadata> channels;

    public P11_ChannelHeaders() {
        this.channels = new HashMap<>();
    }

    @DeserializedConstructor
    public P11_ChannelHeaders(ByteBuf buffer) {
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

    public Map<String, ChannelMetadata> getChannels() {
        return channels;
    }
}
