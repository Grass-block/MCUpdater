package org.atcgroup.mcupdater.client.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.client.ClientError;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.network.ErrorCatchHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.DebugHandler;

import java.net.InetSocketAddress;

public final class ClientNetworkService {
    private final MCUpdaterClient client;
    private Channel channel;

    public ClientNetworkService(MCUpdaterClient client) {
        this.client = client;
    }

    public void run(InetSocketAddress address) {
        var group = new NioEventLoopGroup();
        var bs = new Bootstrap();
        var protocol = MCUProtocolV2.initializer()
                //.handler(DebugHandler::new)
                .handler(ClientFileReceiveHandler::new)
                .handler(() -> new ClientMainHandler(this.client));

        try {
            bs.group(group).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true);
            bs.handler(protocol);

            var cf = bs.connect(address).sync();
            this.channel = cf.channel();
            this.channel.closeFuture().sync();
        } catch (Exception e) {
            this.client.handleException(ClientError.NETWORK, e);
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

    public void write(Packet packet) {
        this.channel.writeAndFlush(packet);
    }
}
