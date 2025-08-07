package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P02_ClientConversationEnd implements Packet {
    @DeserializedConstructor
    public P02_ClientConversationEnd(ByteBuf buffer) {
    }

    public P02_ClientConversationEnd() {

    }

    @Override
    public void write(ByteBuf buffer) {

    }
}
