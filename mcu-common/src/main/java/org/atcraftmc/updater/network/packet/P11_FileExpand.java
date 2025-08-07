package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P11_FileExpand implements Packet {
    private final Map<String, byte[]> list = new HashMap<>();

    @DeserializedConstructor
    public P11_FileExpand(ByteBuf buffer) {
        var count = buffer.readInt();
        for (int i = 0; i < count; i++) {
            var name = BufferUtil.readString(buffer);
            var data = BufferUtil.readArray(buffer);
            this.list.put(name, data);
        }
    }

    public P11_FileExpand() {
        super();
    }

    public P11_FileExpand(Map<String,byte[]> buffer) {
        this.list.putAll(buffer);
    }

    public void add(String path, byte[] data) {
        this.list.put(path, data);
    }

    public Map<String, byte[]> getList() {
        return list;
    }

    @Override
    public void write(ByteBuf buffer) {
        var count = this.list.size();
        buffer.writeInt(count);
        for (var entry : this.list.entrySet()) {
            var name = entry.getKey();
            var data = entry.getValue();
            BufferUtil.writeString(buffer, name);
            BufferUtil.writeArray(buffer, data);
        }
    }
}
