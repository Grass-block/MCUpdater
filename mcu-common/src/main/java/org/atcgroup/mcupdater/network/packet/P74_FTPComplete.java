package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P74_FTPComplete implements Packet {
    public P74_FTPComplete() {
        super();
    }

    @DeserializedConstructor
    public P74_FTPComplete(ByteBuf buffer) {
    }

    @Override
    public void write(ByteBuf byteBuf) {
    }
}
