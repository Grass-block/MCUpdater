package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P71_FTPPayload implements Packet {
    private final long id;
    private final byte[] data;

    @DeserializedConstructor
    public P71_FTPPayload(ByteBuf buffer) {
        this.id = buffer.readLong();
        this.data = BufferUtil.readArray(buffer);
    }

    public P71_FTPPayload(long id, byte[] data) {
        this.data = data;
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeLong(this.id);
        BufferUtil.writeArray(buffer, this.data);
    }
}
