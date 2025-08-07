package org.atcraftmc.updater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcraftmc.updater.client.ClientEventSource;
import org.atcraftmc.updater.client.Event;

public abstract class MCUNetHandler extends PacketInboundHandler implements ClientEventSource {

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        this.handlePacket(packet, ctx);
        ctx.fireChannelRead(packet);
    }

    @Override
    public final void callEvent(Event type, Object... event) {
        ClientEventSource.super.callEvent(type, event);
    }

    @Override
    public final void runTask(Runnable action) {
        ClientEventSource.super.runTask(action);
    }


    public abstract void handlePacket(Packet packet, ChannelHandlerContext ctx) throws Exception;
}
