package org.atcgroup.mcupdater.network.handler.test;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.packet.PF0_PayloadTestRequest;

public final class PacketPayloadReceiver extends PacketInboundHandler {
    long last = System.currentTimeMillis();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) throws Exception {
        System.out.println("received: " + (System.currentTimeMillis() - this.last));
        ctx.writeAndFlush(new PF0_PayloadTestRequest(16384));
        this.last = System.currentTimeMillis();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(new PF0_PayloadTestRequest(16384));
        ctx.fireChannelActive();
    }
}
