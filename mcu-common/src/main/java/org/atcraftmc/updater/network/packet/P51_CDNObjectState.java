package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.util.BufferUtil;

public final class P51_CDNObjectState implements QueryPacket {
    public static final long FILE_NOT_FOUND = -1L;

    private final String owner;
    private final String name;
    private final String queryId;
    private final String checksum;

    @DeserializedConstructor
    public P51_CDNObjectState(ByteBuf buffer) {
        this.queryId = BufferUtil.readString(buffer);
        this.owner = BufferUtil.readString(buffer);
        this.name = BufferUtil.readString(buffer);
        this.checksum = BufferUtil.readString(buffer);
    }

    public P51_CDNObjectState(String owner, String name, String queryId, String checkSum) {
        this.queryId = queryId;
        this.owner = owner;
        this.name = name;
        this.checksum = checkSum;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public String getQueryId() {
        return queryId;
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.queryId);
        BufferUtil.writeString(buffer, this.owner);
        BufferUtil.writeString(buffer, this.name);
        BufferUtil.writeString(buffer, this.checksum);
    }
}
