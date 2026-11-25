package org.atcgroup.mcupdater.server;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.server.service.VersionService;

public class NetworkHandler extends PacketInboundHandler {
    private final VersionService service;

    public NetworkHandler(VersionService service) {
        this.service = service;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {

    }
}
