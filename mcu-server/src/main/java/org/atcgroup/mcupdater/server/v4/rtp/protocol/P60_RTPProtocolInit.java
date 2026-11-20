package org.atcgroup.mcupdater.server.v4.rtp.protocol;

import me.gb2022.simpnet.packet.Packet;
import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;

public final class P60_RTPProtocolInit implements Packet {
    public P60_RTPProtocolInit() {
    }

    @DeserializedConstructor
    public P60_RTPProtocolInit(ByteBuf buffer) {
        RTPProtocol.requireEnd(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        // Initialization has no payload.
    }
}
