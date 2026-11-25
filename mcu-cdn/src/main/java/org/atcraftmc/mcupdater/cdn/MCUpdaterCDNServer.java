package org.atcraftmc.mcupdater.cdn;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.protocol.MCUProtocol;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.network.ErrorCatchHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.NettyHttpFileServer;

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
    private final Map<String, FileRepository> repoChecksumManagers = new HashMap<>();
    private final NettyHttpFileServer server = new NettyHttpFileServer();

    private final Properties props = new Properties();

    public static void main(String[] args) {
        new Thread(INSTANCE).start();
    }

    public FileRepository getRepository(String repo) {
        return this.repoChecksumManagers.computeIfAbsent(repo, (k) -> new FileRepository(FILE_PATH.append(repo)));
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

        if (this.props.containsKey("server.http-port")) {
            try {
                var p = Integer.parseInt(this.props.getProperty("server.http-port"));
                this.server.start(p);

                for (var repo : this.props.keySet()) {
                    if (repo.toString().startsWith("server.")) {
                        continue;
                    }
                    this.server.registerBucket(repo.toString(), getRepository(repo.toString()));
                }

                LOGGER.info("成功启动Http文件传输加速于端点 {}", p);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


        LOGGER.info("正在启动网络服务...");

        var bootstrap = new ServerBootstrap();

        MCUProtocol.serverOptions(bootstrap);

        bootstrap.group(this.bossGroup, this.workerGroup);
        bootstrap.channel(NioServerSocketChannel.class);

        bootstrap.childHandler(MCUProtocolV2.initializer().config((i) -> {
            i.handler(() -> new CDNMainHandler(Integer.parseInt(this.props.getProperty("server.http-port", String.valueOf(-1)))));
            i.handler(() -> new CDNUploadReceiveHandler(this::getRepository));
            i.handler(ErrorCatchHandler::new);
        }));

        try {
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
