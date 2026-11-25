package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import org.atcgroup.mcupdater.network.packet.EmptyPacket;

public final class P10_ChannelHeaderRequest extends EmptyPacket {
    public P10_ChannelHeaderRequest() {
        super();
    }

    @DeserializedConstructor
    public P10_ChannelHeaderRequest(ByteBuf byteBuf) {
        super(byteBuf);
    }
}
