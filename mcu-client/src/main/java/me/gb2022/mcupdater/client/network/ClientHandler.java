package me.gb2022.mcupdater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.mcupdater.client.MCUpdaterClient;
import me.gb2022.mcupdater.protocol.P02_ServerHandshake;
import me.gb2022.mcupdater.protocol.P11_ChannelHeaders;
import me.gb2022.mcupdater.protocol.P13_VersionHeaders;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;

import java.util.HashSet;

public final class ClientHandler extends PacketInboundHandler {
    private final MCUpdaterClient client;

    public ClientHandler(MCUpdaterClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P02_ServerHandshake message) {
            this.client.handleServerConnected(message);
            return;
        }
        if (packet instanceof P11_ChannelHeaders message) {
            this.client.handleConfig(new HashSet<>(message.getChannels().values()));
            return;
        }
        if (packet instanceof P13_VersionHeaders message) {
            this.client.handleVersionData(message.getHeader());
            return;
        }

        ctx.fireChannelRead(packet);
    }
}
