package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P13_PatchFileInfo implements Packet {
    private final int length;
    private final long created;
    private final String sha256;

    public P13_PatchFileInfo(int length, long created, String sha256) {
        this.length = length;
        this.created = created;
        this.sha256 = sha256;
    }

    @DeserializedConstructor
    public P13_PatchFileInfo(ByteBuf buffer) {
        this.length = buffer.readInt();
        this.created = buffer.readLong();
        this.sha256 = BufferUtil.readString(buffer);
    }

    @Override
    public void write(ByteBuf byteBuf) {
        byteBuf.writeInt(this.length);
        byteBuf.writeLong(this.created);
        BufferUtil.writeString(byteBuf, this.sha256);
    }

    public int getLength() {
        return length;
    }

    public String getSha256() {
        return sha256;
    }

    public long getCreated() {
        return created;
    }
}
