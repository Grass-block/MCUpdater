package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P70_FTPHeader implements Packet {
    private final String user;
    private final String token;
    private final String filename;
    private final byte[] sha256;
    private final long totalLength;
    private final long totalPackets;

    public P70_FTPHeader(String user, String token, String filename, byte[] sha256, long totalLength, long totalPackets) {
        this.user = user;
        this.token = token;
        this.filename = filename;
        this.sha256 = sha256;
        this.totalLength = totalLength;
        this.totalPackets = totalPackets;
    }

    @DeserializedConstructor
    public P70_FTPHeader(ByteBuf buffer) {
        this.user = BufferUtil.readString(buffer);
        this.token = BufferUtil.readString(buffer);
        this.filename = BufferUtil.readString(buffer);
        this.sha256 = BufferUtil.readArray(buffer);
        this.totalLength = buffer.readLong();
        this.totalPackets = buffer.readLong();
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.user);
        BufferUtil.writeString(buffer, this.token);
        BufferUtil.writeString(buffer, this.filename);
        BufferUtil.writeArray(buffer, this.sha256);
        buffer.writeLong(this.totalLength);
        buffer.writeLong(this.totalPackets);
    }

    public String getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public String getFilename() {
        return filename;
    }

    public long getTotalLength() {
        return totalLength;
    }

    public byte[] getSha256() {
        return sha256;
    }

    public long getTotalPackets() {
        return totalPackets;
    }
}
