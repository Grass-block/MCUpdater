package org.atcgroup.mcupdater.network.handler.test;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.PF0_PayloadTestRequest;
import org.atcgroup.mcupdater.network.packet.PF1_PayloadTestResponse;

public final class PacketPayloadProvider extends PacketInboundHandler {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) throws Exception {
        if (msg instanceof PF0_PayloadTestRequest pf0) {
            System.out.println("receive: " + ctx.channel().remoteAddress());
            MCUProtocolV2.sendPacket(ctx.channel(), new PF1_PayloadTestResponse(pf0.getLen()));
        }
    }
}
