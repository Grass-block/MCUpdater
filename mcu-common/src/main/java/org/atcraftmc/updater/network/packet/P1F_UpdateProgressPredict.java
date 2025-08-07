package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P1F_UpdateProgressPredict implements Packet {
    private final long size;

    public P1F_UpdateProgressPredict(long size) {
        this.size = size;
    }

    @DeserializedConstructor
    public P1F_UpdateProgressPredict(ByteBuf buf) {
        size = buf.readLong();
    }

    @Override
    public void write(ByteBuf byteBuf) {
        byteBuf.writeLong(size);
    }

    public long getSize() {
        return size;
    }
}
