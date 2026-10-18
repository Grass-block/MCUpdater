package org.atcgroup.mcupdater.server.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.P01_ServerHello;
import org.atcgroup.mcupdater.server.MCUpdaterServer;
import org.atcgroup.mcupdater.util.I18n;
import org.bukkit.configuration.ConfigurationSection;

import static org.atcgroup.mcupdater.server.MCUpdaterServer.LOGGER;

public final class NetworkService implements Service {
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();


    @Override
    public void handleServerBootstrap(MCUpdaterServer server,ConfigurationSection config) {
        var address = config.getString("server-address");
        var port = config.getInt("server-port");

        LOGGER.info(I18n.message("network.start"));

        try {
            var bootstrap = new ServerBootstrap();
            var i = MCUProtocolV2.initializer();

            i.handler(() -> new ServerHandler(MCUpdaterServer.instance()));

            MCUpdaterServer.instance().getServiceManager().fireNetworkBootstrap(MCUpdaterServer.instance(),i);
            MCUProtocolV2.serverOptions(bootstrap);

            bootstrap.group(this.bossGroup, this.workerGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(i);
            bootstrap.bind(port).sync();

            LOGGER.info(I18n.message("network.started", address, port));
        } catch (Exception e) {
            LOGGER.catching(e);
            LOGGER.info(I18n.message("network.error"));
            MCUpdaterServer.instance().stop();
        }
    }

    @Override
    public void handleServerClose(MCUpdaterServer server) {
        try {
            LOGGER.info(I18n.message("network.stop"));
            this.bossGroup.shutdownGracefully();
            this.workerGroup.shutdownGracefully();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    //here's only client connection.
    public static final class ServerHandler extends PacketInboundHandler {
        public static final Logger LOGGER = LogManager.getLogger("Network");

        private final MCUpdaterServer server;

        public ServerHandler(MCUpdaterServer server) {
            this.server = server;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            var session = this.server.createSession();
            LOGGER.info(I18n.message("network.session.created", session.getSessionId()));
            MCUProtocolV2.sendPacket(ctx, new P01_ServerHello(session));
            ctx.fireChannelActive();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
            this.server.getServiceManager().fireNetworkMessage(packet, ctx);
            ctx.fireChannelRead(packet);
        }
    }
}
