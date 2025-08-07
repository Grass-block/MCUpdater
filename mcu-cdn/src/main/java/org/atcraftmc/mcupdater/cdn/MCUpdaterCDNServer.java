package org.atcraftmc.mcupdater.cdn;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcraftmc.mcupdater.cdn.handler.CDNClientSideHandler;
import org.atcraftmc.mcupdater.cdn.handler.CDNServerSideHandler;
import org.atcraftmc.updater.network.handler.ErrorCatchHandler;
import org.atcraftmc.updater.network.handler.HeartBeatHandler;
import org.atcraftmc.updater.network.MCUProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MCUpdaterCDNServer implements Runnable {
    public static final MCUpdaterCDNServer INSTANCE = new MCUpdaterCDNServer();

    public static final Logger LOGGER = LogManager.getLogger("MCU-CDNServer");
    public static final ExecutorService WORKER = Executors.newCachedThreadPool();
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();

    private final Properties props = new Properties();

    public static void main(String[] args) {
        new Thread(INSTANCE).start();
    }

    @Override
    public void run() {
        var prop = new File(System.getProperty("user.dir") + File.separator + "config.properties");

        if (!prop.exists() || prop.length() == 0) {
            prop.getParentFile().mkdirs();
            try {
                prop.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("请按文档配置好CDN端后再启动: https://wiki.atcraftmc.cn/other/mcu/cdn.html");
            return;
        }

        try {
            this.props.load(new FileInputStream(prop));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var address = this.props.getProperty("server.address");
        var port = this.props.getProperty("server.port");

        LOGGER.info("正在启动网络服务...");

        try {
            ServerBootstrap sbs = new ServerBootstrap();
            sbs.group(this.bossGroup, this.workerGroup);
            sbs.channel(NioServerSocketChannel.class);
            sbs.option(ChannelOption.SO_BACKLOG, 128);
            sbs.childOption(ChannelOption.SO_KEEPALIVE, true);
            sbs.handler(new LoggingHandler(LogLevel.INFO));
            sbs.childHandler(MCUProtocol.initializer().handler(HeartBeatHandler::new).handler(ErrorCatchHandler::new).handler(CDNServerSideHandler::new).handler(CDNClientSideHandler::new));

            var cf = sbs.bind(Integer.parseInt(port)).sync();

            LOGGER.info("成功启动网络服务于 {}:{}", address, port);

            cf.channel().closeFuture().sync();
        } catch (Exception e) {
            LOGGER.catching(e);
            LOGGER.info("网络管线出现错误, 正在关闭服务器...");
        }
    }

    public void stop() {
        LOGGER.info("正在关闭网络管线...");
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }

    public Properties config() {
        return this.props;
    }
}
