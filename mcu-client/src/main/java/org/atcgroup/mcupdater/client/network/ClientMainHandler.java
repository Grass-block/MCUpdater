package org.atcgroup.mcupdater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.network.packet.P01_ServerHello;
import org.atcgroup.mcupdater.network.packet.P20_ChannelHeaders;
import org.atcgroup.mcupdater.network.packet.P21_VersionHeaders;
import org.atcgroup.mcupdater.network.packet.P22_UpdateLogs;

public final class ClientMainHandler extends PacketInboundHandler {
    private final MCUpdaterClient client;

    public ClientMainHandler(MCUpdaterClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        System.out.println(packet);

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

        if (packet instanceof P22_UpdateLogs message) {
            return;
        }

        ctx.fireChannelRead(packet);
    }
}
