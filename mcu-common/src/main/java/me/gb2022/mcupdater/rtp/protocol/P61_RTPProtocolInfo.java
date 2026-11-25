package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P61_RTPProtocolInfo implements Packet {
    private final int maxWorker;
    private final int transportMTU;
    private final boolean supportsHTTP;
    private final String httpEndpoint;

    @DeserializedConstructor
    public P61_RTPProtocolInfo(ByteBuf buffer) {
        this(buffer.readInt(), buffer.readInt(), buffer.readBoolean(), BufferUtil.readString(buffer));
        RTPProtocol.requireEnd(buffer);
    }

    public P61_RTPProtocolInfo(int maxWorker, int transportMTU, boolean supportsHTTP, String httpEndpoint) {
        if (maxWorker < 1 || transportMTU < 1 || httpEndpoint == null || httpEndpoint.length() > 2048) {
            throw new IllegalArgumentException("Invalid RTP capabilities");
        }
        if (supportsHTTP) {
            var endpoint = java.net.URI.create(httpEndpoint);
            if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                    || endpoint.getUserInfo() != null) throw new IllegalArgumentException("Invalid HTTP endpoint");
        }
        this.maxWorker = maxWorker;
        this.transportMTU = transportMTU;
        this.supportsHTTP = supportsHTTP;
        this.httpEndpoint = httpEndpoint;
    }

    public int getMaxWorker() {
        return maxWorker;
    }

    public int getTransportMTU() {
        return transportMTU;
    }

    public String getHttpEndpoint() {
        return httpEndpoint;
    }

    public boolean supportsHTTP() {
        return supportsHTTP;
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.maxWorker);
        buffer.writeInt(this.transportMTU);
        buffer.writeBoolean(this.supportsHTTP);
        BufferUtil.writeString(buffer, this.httpEndpoint);
    }
}
