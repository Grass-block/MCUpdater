package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.data.ServerMeta;

public final class P01_ServerHello implements Packet {
    private final ServerMeta serverMeta;

    public P01_ServerHello(ServerMeta meta) {
        this.serverMeta = meta;
    }

    @DeserializedConstructor
    public P01_ServerHello(ByteBuf buffer) {
        var serverBrand = BufferUtil.readString(buffer);
        var serverVersion = BufferUtil.readString(buffer);
        var sessionId = BufferUtil.readString(buffer);
        var hasCDNInfo = buffer.readBoolean();
        var cdnHost = BufferUtil.readString(buffer);
        var cdnPort = buffer.readInt();
        var cdnRepo = BufferUtil.readString(buffer);

        this.serverMeta = new ServerMeta(serverBrand, serverVersion, sessionId, hasCDNInfo, cdnHost, cdnPort, cdnRepo);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.serverMeta.getServerBrand());
        BufferUtil.writeString(buffer, this.serverMeta.getServerVersion());
        BufferUtil.writeString(buffer, this.serverMeta.getSessionId());
        buffer.writeBoolean(this.serverMeta.hasCDNInfo());
        BufferUtil.writeString(buffer, this.serverMeta.getCdnHost());
        buffer.writeInt(this.serverMeta.getCdnPort());
        BufferUtil.writeString(buffer, this.serverMeta.getCdnRepository());
    }

    public ServerMeta getServerMeta() {
        return serverMeta;
    }
}
