package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P02_ServerHandshake implements Packet {
    private final boolean hasCDNServer;
    private final String cdnHost;
    private final int cdnPort;

    public P02_ServerHandshake(boolean hasCDNServer, String cdnHost, int cdnPort) {
        this.hasCDNServer = hasCDNServer;
        this.cdnHost = cdnHost;
        this.cdnPort = cdnPort;
    }

    @DeserializedConstructor
    public P02_ServerHandshake(ByteBuf buffer) {
        this(buffer.readBoolean(), BufferUtil.readString(buffer), buffer.readInt());
    }

    public static P02_ServerHandshake noCDN() {
        return new P02_ServerHandshake( false, "_", -1);
    }

    public static P02_ServerHandshake hasCDN(String cdnHost, int cdnPort) {
        return new P02_ServerHandshake(true, cdnHost, cdnPort);
    }

    public boolean hasCDNServer() {
        return hasCDNServer;
    }

    public String getCdnHost() {
        return cdnHost;
    }

    public int getCdnPort() {
        return cdnPort;
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeBoolean(this.hasCDNServer);
        BufferUtil.writeString(buffer, this.cdnHost);
        buffer.writeInt(this.cdnPort);
    }
}
