package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.util.BufferUtil;

public final class P50_CDNCheckObjectStatus implements QueryPacket {
    private final String queryId;
    private final String owner;
    private final String name;
    private final String token;

    @DeserializedConstructor
    public P50_CDNCheckObjectStatus(ByteBuf buffer) {
        this.queryId = BufferUtil.readString(buffer);
        this.owner = BufferUtil.readString(buffer);
        this.name = BufferUtil.readString(buffer);
        this.token = BufferUtil.readString(buffer);
    }

    public P50_CDNCheckObjectStatus(String queryId, String owner, String name, String token) {
        this.queryId = queryId;
        this.owner = owner;
        this.name = name;
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    public String getOwner() {
        return owner;
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
        BufferUtil.writeString(buffer, this.token);
    }
}
