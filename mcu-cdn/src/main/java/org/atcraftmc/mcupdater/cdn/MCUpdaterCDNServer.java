package org.atcraftmc.mcupdater.cdn;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import me.gb2022.commons.file.FilePath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.ErrorCatchHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.HeartBeatHandler;
import org.atcgroup.mcupdater.util.FileChecksumManager;
import org.atcraftmc.mcupdater.cdn.handler.CDNMainHandler;
import org.atcraftmc.mcupdater.cdn.handler.CDNUploadReceiveHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class MCUpdaterCDNServer implements Runnable {
    public static FilePath FILE_PATH = FilePath.RUNTIME;

    public static final MCUpdaterCDNServer INSTANCE = new MCUpdaterCDNServer();

    public static final Logger LOGGER = LogManager.getLogger("MCU-CDNServer");
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private final FileStatusManager fileManager = new FileStatusManager();
    private final Map<String, FileChecksumManager> repoChecksumManagers = new HashMap<>();

    private final Properties props = new Properties();

    public static void main(String[] args) {
        new Thread(INSTANCE).start();
    }

    public FileChecksumManager getRepoChecksumManager(String repo) {
        return this.repoChecksumManagers.computeIfAbsent(repo, (k) -> new FileChecksumManager(FILE_PATH.append(repo)));
    }

    public FileStatusManager getFileManager() {
        return fileManager;
    }

    @Override
    public void run() {
        var prop = FILE_PATH.append("config.properties").file();

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
            var bootstrap = new ServerBootstrap();

            bootstrap.group(this.bossGroup, this.workerGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.option(ChannelOption.SO_BACKLOG, 128);
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.handler(new LoggingHandler(LogLevel.WARN));

            var i = MCUProtocolV2.initializer();

            i.handler(HeartBeatHandler::new);
            i.handler(ErrorCatchHandler::new);
            i.handler(() -> new CDNUploadReceiveHandler(this.fileManager));
            i.handler(CDNMainHandler::new);

            bootstrap.childHandler(i);

            var cf = bootstrap.bind(Integer.parseInt(port)).sync();

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
