package org.atcgroup.mcupdater.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.commons.file.FilePath;
import me.gb2022.commons.math.SHA;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.DebugHandler;
import org.atcgroup.mcupdater.network.handler.FileDownloadHandler;
import org.atcgroup.mcupdater.network.handler.FileReceiveHandler;
import org.atcgroup.mcupdater.network.packet.P70_FTPHeader;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface Test {

    static void testServer(FileDownloadHandler handler) {
        var bossGroup = new NioEventLoopGroup();
        var workerGroup = new NioEventLoopGroup();

        try {
            var sbs = new ServerBootstrap();
            var i = MCUProtocolV2.initializer();

            i.handler(() -> handler);
            i.handler(DebugHandler::new);

            sbs.group(bossGroup, workerGroup);
            sbs.channel(NioServerSocketChannel.class);
            sbs.option(ChannelOption.SO_BACKLOG, 128);
            sbs.childOption(ChannelOption.SO_KEEPALIVE, true);
            sbs.childHandler(i);
            sbs.bind(65320).sync();
        } catch (Exception ignored) {
        }
    }

    static void testClient() {
        var group = new NioEventLoopGroup();
        var bs = new Bootstrap();
        var protocol = MCUProtocolV2.initializer()
                .handler(DebugHandler::new)
                .handler(() -> new FileReceiveHandler() {
                    private static final Logger LOGGER = LogManager.getLogger("FTPClient");

                    @Override
                    public File getFile(P70_FTPHeader header) {
                        return FilePath.RUNTIME.append("dest").append(header.getFilename()).file();
                    }

                    @Override
                    public void onWriteStart(File file, P70_FTPHeader header) {
                        var sha = SHA.byteArrayToHexString(header.getSha256());
                        var name = header.getFilename();
                        var len = header.getTotalLength();

                        LOGGER.info("receiving {} ({}bytes), sha= {}", name, len, sha);
                    }

                    @Override
                    public void onWriteComplete(String user, File file) {
                        LOGGER.info("received file({}).", file.getName());
                    }
                });
        try {
            bs.group(group).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true);
            bs.handler(protocol);
            bs.connect(new InetSocketAddress("127.0.0.1", 65320)).sync();
        } catch (Exception ignored) {
        } finally {
            group.shutdownGracefully();
        }
    }


    static void main(String[] args) {
        var sendFiles = new ArrayList<>(List.of(Objects.requireNonNull(FilePath.RUNTIME.append("source").file().listFiles())));
        var sender = new FileDownloadHandler("", "", sendFiles);

        testServer(sender);
        testClient();

        try {
            sender.push();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
