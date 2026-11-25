package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P30_CDNAuthenticate implements Packet {
    private final String serverId;
    private final String token;

    public P30_CDNAuthenticate(String serverId, String token) {
        if (serverId == null || serverId.isBlank() || serverId.length() > 128 || token == null || token.isBlank() || token.length() > 512) {
            throw new IllegalArgumentException("Invalid CDN credentials");
        }
        this.serverId = serverId;
        this.token = token;
    }

    @DeserializedConstructor
    public P30_CDNAuthenticate(ByteBuf buffer) {
        this(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        requireEnd(buffer);
    }

    @Override public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, serverId);
        BufferUtil.writeString(buffer, token);
    }

    public String serverId() { return serverId; }
    public String token() { return token; }

    private static void requireEnd(ByteBuf buffer) {
        if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected CDN authentication payload");
    }
}
