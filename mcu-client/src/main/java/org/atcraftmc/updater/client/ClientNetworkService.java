package org.atcraftmc.updater.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.atcraftmc.updater.client.network.ClientNetworkHandler;
import org.atcraftmc.updater.client.network.FileOperationHandler;
import org.atcraftmc.updater.client.network.PatchFileHandler;
import org.atcraftmc.updater.network.MCUProtocol;

public final class ClientNetworkService extends Thread implements ClientEventSource {
    private final String ip;
    private final int port;
    private Channel channel;

    public ClientNetworkService(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public void run() {
        var group = new NioEventLoopGroup();
        var bs = new Bootstrap();
        var protocol = MCUProtocol.initializer()
                .handler(PatchFileHandler::new)
                .handler(FileOperationHandler::new)
                .handler(ClientNetworkHandler::new);

        try {
            bs.group(group).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true);
            bs.handler(protocol);


            callEvent(Event.PROGRESS, "正在连接到更新服务器...");
            var cf = bs.connect(this.ip, this.port).sync();
            this.channel = cf.channel();
            callEvent(Event.PROGRESS, "成功连接到服务器!");
            callEvent(Event.CONNECT_SUCCESS);
            this.channel.closeFuture().sync();
        } catch (Exception e) {
            callEvent(Event.EXCEPTION, e);
        } finally {
            group.shutdownGracefully();
        }
    }

    public Channel channel() {
        return this.channel;
    }

    public void shutdown() {
        this.channel.disconnect();
    }
}
