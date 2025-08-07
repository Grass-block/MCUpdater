package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P21_VersionLogInfo implements Packet {
    private final String content;

    public P21_VersionLogInfo(String content) {
        this.content = content;
    }

    @DeserializedConstructor
    public P21_VersionLogInfo(ByteBuf buffer) {
        this.content = BufferUtil.readString(buffer);
    }

    @Override
    public void write(ByteBuf buf) {
        BufferUtil.writeString(buf, this.content);
    }

    public String getContent() {
        return content;
    }
}
