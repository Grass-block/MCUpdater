package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P00_KeepAlive implements Packet {
    private final long time;

    @DeserializedConstructor
    public P00_KeepAlive(ByteBuf buffer) {
        this.time = buffer.readLong();
    }

    public P00_KeepAlive() {
        this.time = System.currentTimeMillis();
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeLong(this.time);
    }

    public long getTime() {
        return time;
    }
}
