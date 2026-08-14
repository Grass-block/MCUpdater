package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P02_CDNHandShake implements Packet {
    private final String repo;

    public P02_CDNHandShake(String repo) {
        this.repo = repo;
    }

    @DeserializedConstructor
    public P02_CDNHandShake(ByteBuf buffer) {
        this.repo = BufferUtil.readString(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.repo);
    }

    public String getRepo() {
        return repo;
    }
}
