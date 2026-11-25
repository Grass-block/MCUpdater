package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P31_CDNAuthenticationResult implements Packet {
    private final boolean accepted;
    private final String message;

    public P31_CDNAuthenticationResult(boolean accepted, String message) {
        if (message == null || message.length() > 1024) throw new IllegalArgumentException("Invalid result message");
        this.accepted = accepted;
        this.message = message;
    }

    @DeserializedConstructor
    public P31_CDNAuthenticationResult(ByteBuf buffer) {
        this(buffer.readBoolean(), BufferUtil.readString(buffer));
        if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected CDN result payload");
    }

    @Override public void write(ByteBuf buffer) {
        buffer.writeBoolean(accepted);
        BufferUtil.writeString(buffer, message);
    }

    public boolean accepted() { return accepted; }
    public String message() { return message; }
}
