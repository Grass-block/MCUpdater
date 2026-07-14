package org.atcgroup.mcupdater.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import me.gb2022.commons.file.FilePath;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.DebugHandler;
import org.atcgroup.mcupdater.network.handler.FileServerHandler;
import org.atcgroup.mcupdater.server.network.ServerHandler;

import static org.atcraftmc.updater.server.MCUpdaterServer.LOGGER;

public final class NetworkService {
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();

    public void start() {
        var config = MCUpdaterServer.instance().config();

        var address = config.getString("server-address");
        var port = config.getInt("server-port");

        LOGGER.info("Starting network service...");

        try {
            var sbs = new ServerBootstrap();
            var i = MCUProtocolV2.initializer();

            i.handler(DebugHandler::new);
            i.handler(() -> new ServerHandler(MCUpdaterServer.instance()));
            i.handler(() -> new FileServerHandler(FilePath.RUNTIME.append("/packs")));

            sbs.group(this.bossGroup, this.workerGroup);
            sbs.channel(NioServerSocketChannel.class);
            sbs.option(ChannelOption.SO_BACKLOG, 128);
            sbs.childOption(ChannelOption.SO_KEEPALIVE, true);
            sbs.handler(new LoggingHandler(LogLevel.INFO));
            sbs.childHandler(i);
            sbs.bind(port).sync();

            LOGGER.info("Network server started on {}:{}", address, port);
        } catch (Exception e) {
            LOGGER.catching(e);
            LOGGER.info("Network error occurred, stopping server...");
            MCUpdaterServer.instance().stop();
        }
    }

    public void stop() {
        LOGGER.info("Stopping network service...");
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }
}
