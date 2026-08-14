package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public abstract class EmptyPacket implements Packet {
    public EmptyPacket() {
    }

    @DeserializedConstructor
    public EmptyPacket(ByteBuf byteBuf) {

    }

    @Override
    public final void write(ByteBuf byteBuf) {

    }
}
