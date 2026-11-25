package me.gb2022.mcupdater.cdn;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import me.gb2022.mcupdater.protocol.MCUProtocol;
import me.gb2022.simpnet.channel.NettyChannelInitializer;

import java.util.function.Consumer;

public final class CDNNetworkServer {
    private final ChannelGroup connections = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();

    private Channel listener;
    private volatile boolean closed = false;

    public void run(int port, Consumer<NettyChannelInitializer> initializer) throws Exception {
        var protocol = MCUProtocol.initializer();
        var bootstrap = new ServerBootstrap();

        initializer.accept(protocol);

        bootstrap.group(this.bossGroup, this.workerGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        MCUProtocol.serverOptions(bootstrap);
        bootstrap.childHandler(protocol);
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socket) {
                connections.add(socket);
            }
        });

        this.listener = bootstrap.bind(port).sync().channel();

        Runtime.getRuntime().addShutdownHook(new Thread(this::close));

        try {
            listener.closeFuture().sync();
        } finally {
            close();
        }
    }

    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;

        connections.close().awaitUninterruptibly();
        if (listener != null) {
            listener.close().syncUninterruptibly();
        }
        this.bossGroup.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
        this.workerGroup.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
    }
}
