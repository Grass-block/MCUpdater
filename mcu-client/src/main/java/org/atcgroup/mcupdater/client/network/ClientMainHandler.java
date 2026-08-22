package org.atcgroup.mcupdater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.network.packet.*;

public final class ClientMainHandler extends PacketInboundHandler {
    private final MCUpdaterClient client;

    public ClientMainHandler(MCUpdaterClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P01_ServerHello message) {
            this.client.handleConnected(message);
            return;
        }

        if (packet instanceof P20_ChannelHeaders message) {
            this.client.handleConfigReceived(message);
            return;
        }

        if (packet instanceof P21_VersionHeaders message) {
            this.client.handleUpdaterHeaderReceived(message);
            return;
        }

        if (packet instanceof P32_FileDownloadComplete) {
            this.client.handleResourceDownloadComplete();
            return;
        }

        if (packet instanceof P22_UpdateLogs message) {
            this.client.handleLogReceived(message);
            return;
        }

        ctx.fireChannelRead(packet);
    }
}
