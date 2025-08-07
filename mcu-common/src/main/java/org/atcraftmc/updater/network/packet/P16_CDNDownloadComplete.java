package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P16_CDNDownloadComplete implements Packet {
    private final String sessionId;

    @DeserializedConstructor
    public P16_CDNDownloadComplete(ByteBuf buffer) {
        this.sessionId = BufferUtil.readString(buffer);
    }

    public P16_CDNDownloadComplete(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public void write(ByteBuf byteBuf) {
        BufferUtil.writeString(byteBuf, this.sessionId);
    }

    public String getSessionId() {
        return sessionId;
    }
}
