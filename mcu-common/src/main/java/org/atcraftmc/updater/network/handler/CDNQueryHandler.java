package org.atcraftmc.updater.network.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcraftmc.updater.network.packet.QueryPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@ChannelHandler.Sharable
public final class CDNQueryHandler extends PacketInboundHandler {
    private final Map<String, CompletableFuture<QueryPacket>> futures = new HashMap<>();
    private Channel channel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        ctx.fireChannelActive();
    }

    public <I extends QueryPacket> Future<I> sendQueryPacket(QueryPacket query, Class<I> result) {
        this.channel.writeAndFlush(query);

        var future = new CompletableFuture<I>();
        this.futures.put(query.getQueryId(), (CompletableFuture<QueryPacket>) future);
        return future;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (!(packet instanceof QueryPacket qp)) {
            ctx.fireChannelRead(packet);
            return;
        }

        var sid = qp.getQueryId();

        if (this.futures.containsKey(sid)) {
            var future = this.futures.get(sid);
            future.complete((QueryPacket) packet);
            this.futures.remove(sid);
        }
    }

    public void clear() {
        this.futures.clear();
    }
}
