package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;

public final class P10_ChannelHeaderRequest extends EmptyPacket {
    public P10_ChannelHeaderRequest() {
        super();
    }

    @DeserializedConstructor
    public P10_ChannelHeaderRequest(ByteBuf byteBuf) {
        super(byteBuf);
    }
}
