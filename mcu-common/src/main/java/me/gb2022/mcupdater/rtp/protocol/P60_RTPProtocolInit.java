package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

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
