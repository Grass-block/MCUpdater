package me.gb2022.mcupdater.client.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.mcupdater.client.ClientError;
import me.gb2022.mcupdater.client.MCUpdaterClient;
import me.gb2022.mcupdater.protocol.MCUProtocol;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.network.ErrorCatchHandler;

import java.net.InetSocketAddress;

public final class ClientConnector {
    private final MCUpdaterClient client;
    private Channel channel;

    public ClientConnector(MCUpdaterClient client) {
        this.client = client;
    }

    public void run(InetSocketAddress address) {
        var group = new NioEventLoopGroup();
        var bs = new Bootstrap();
        var protocol = MCUProtocol.initializer()
                                    .handler(() -> new ClientHandler(this.client))
                                    .handler(ErrorCatchHandler::new);

        MCUProtocol.clientOptions(bs);

        try {
            bs.group(group).channel(NioSocketChannel.class);
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
        if(this.channel == null){
            return;
        }
        this.channel.disconnect();
    }

    public void write(Packet packet) {
        this.channel.writeAndFlush(packet);
    }
}
