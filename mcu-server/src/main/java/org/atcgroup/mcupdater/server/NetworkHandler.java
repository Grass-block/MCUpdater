package org.atcgroup.mcupdater.server;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.server.file.FileSource;
import org.atcgroup.mcupdater.server.service.VersionService;

import java.util.stream.Collectors;

public class NetworkHandler extends PacketInboundHandler {
    private final VersionService service;

    public NetworkHandler(VersionService service) {
        this.service = service;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {

    }
}
