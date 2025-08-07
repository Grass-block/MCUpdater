package org.atcraftmc.updater.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.simpnet.packet.Packet;
import org.atcraftmc.updater.network.handler.ErrorCatchHandler;
import org.atcraftmc.updater.network.handler.HeartBeatHandler;
import org.atcraftmc.updater.network.handler.CDNQueryHandler;
import org.atcraftmc.updater.network.packet.QueryPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class CDNClient implements Runnable {
    private final CDNQueryHandler queryHandler = new CDNQueryHandler();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final List<Supplier<ChannelHandler>> extraHandlers = new ArrayList<>();

    private final String ip;
    private final int port;
    private Channel channel;

    public CDNClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public CDNClient addHandler(Supplier<ChannelHandler> handler) {
        this.extraHandlers.add(handler);
        return this;
    }

    @Override
    public void run() {
        this.running.set(true);

        this.queryHandler.clear();
        var group = new NioEventLoopGroup();
        var bs = new Bootstrap();
        var protocol = MCUProtocol.initializer().handler(HeartBeatHandler::new).handler(() -> this.queryHandler).handler(ErrorCatchHandler::new);

        for (var handler : this.extraHandlers) {
            protocol.handler(handler);
        }

        try {
            bs.group(group).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true);
            bs.handler(protocol);
            var cf = bs.connect(this.ip, this.port).sync();
            this.ready.set(true);
            this.channel = cf.channel();
            this.channel.closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
            this.running.set(false);
            this.ready.set(false);
        }
    }

    public Channel channel() {
        return this.channel;
    }

    public void shutdown() {
        this.channel.disconnect();
    }

    public void start() {
        new Thread(this).start();
    }

    public <I extends QueryPacket> Future<I> sendQuery(QueryPacket packet, Class<I> result) {
        if (!this.running.get()) {
            this.start();
        }

        while (!this.ready.get()) {
            Thread.yield();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return this.queryHandler.sendQueryPacket(packet, result);
    }

    public void send(Packet msg) {
        this.channel.writeAndFlush(msg);
    }
}
