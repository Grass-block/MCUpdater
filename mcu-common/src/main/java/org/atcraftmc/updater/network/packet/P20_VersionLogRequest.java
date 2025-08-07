package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P20_VersionLogRequest implements Packet {
    private final String version;

    public P20_VersionLogRequest(String version) {
        this.version = version;
    }

    @DeserializedConstructor
    public P20_VersionLogRequest(ByteBuf buffer) {
        this.version = BufferUtil.readString(buffer);
    }

    @Override
    public void write(ByteBuf buf) {
        BufferUtil.writeString(buf, this.version);
    }

    public String getVersion() {
        return version;
    }
}
