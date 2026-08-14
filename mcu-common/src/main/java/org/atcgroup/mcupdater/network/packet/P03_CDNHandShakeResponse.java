package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;

public final class P03_CDNHandShakeResponse extends EmptyPacket {
    @DeserializedConstructor
    public P03_CDNHandShakeResponse(ByteBuf buffer) {
    }

    public P03_CDNHandShakeResponse() {
        super();
    }
}
