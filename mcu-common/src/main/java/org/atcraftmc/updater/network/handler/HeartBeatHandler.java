package org.atcraftmc.updater.network.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.EventExecutor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcraftmc.updater.network.Tickable;
import org.atcraftmc.updater.network.packet.P00_KeepAlive;

import java.util.concurrent.TimeUnit;

public final class HeartBeatHandler extends PacketInboundHandler implements Tickable {
    private EventExecutor executor;
    private long latency;
    private long ticks;
    private long netTicks;
    private Channel channel;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        this.executor = ctx.executor();

        this.netTicks = 0;
        this.latency = 0;

        // 固定速率（严格按时间间隔执行）
        this.executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
        ctx.fireChannelActive();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (!(packet instanceof P00_KeepAlive ka)) {
            ctx.fireChannelRead(packet);
            return;
        }

        this.latency = ka.getTime() - System.currentTimeMillis();
        this.netTicks++;
        this.ticks = this.netTicks;
    }

    public long getLatency() {
        return latency;
    }

    @Override
    public void tick() {
        this.channel.writeAndFlush(new P00_KeepAlive());
        if (this.netTicks < this.ticks - 32) {
            this.channel.disconnect();
        }

        this.ticks++;
    }
}
