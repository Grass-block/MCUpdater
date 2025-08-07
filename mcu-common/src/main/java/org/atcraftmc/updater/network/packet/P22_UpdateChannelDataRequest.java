package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P22_UpdateChannelDataRequest implements Packet {

    public P22_UpdateChannelDataRequest() {
        super();
    }

    @DeserializedConstructor
    public P22_UpdateChannelDataRequest(ByteBuf buffer) {

    }

    @Override
    public void write(ByteBuf byteBuf) {

    }
}
