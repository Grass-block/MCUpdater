package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P73_FTPCancel implements Packet {
    public P73_FTPCancel() {
        super();
    }

    @DeserializedConstructor
    public P73_FTPCancel(ByteBuf buffer) {
    }

    @Override
    public void write(ByteBuf byteBuf) {
    }
}
