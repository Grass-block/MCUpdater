package org.atcgroup.mcupdater.server.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcraftmc.updater.network.MCUProtocol;
import org.atcgroup.mcupdater.network.handler.FileDownloadHandler;
import org.atcgroup.mcupdater.network.ErrorCatchHandler;
import org.atcgroup.mcupdater.network.handler.HeartBeatHandler;

import java.io.File;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;

public final class CDNUploadWorker extends ChannelInboundHandlerAdapter implements Runnable {
    public static final Logger LOGGER = LogManager.getLogger("CDN-UploadWorker");

    private final SocketAddress address;
    private final FileDownloadHandler task;

    public CDNUploadWorker(SocketAddress address, String cdnAccount, String cdnToken, Collection<File> files) {
        this.address = address;
        this.task = new FileDownloadHandler(cdnAccount, cdnToken, new ArrayList<>(files));
    }

    public static void start(SocketAddress address, String cdnAccount, String cdnToken, Collection<File> files) {
        var worker = new CDNUploadWorker(address, cdnAccount, cdnToken, files);
        var thread = new Thread(worker, "CDN-uploader-" + worker.hashCode());

        thread.start();
    }

    @Override
    public void run() {
        var group = new NioEventLoopGroup();
        var bs = new Bootstrap();
        var protocol = MCUProtocol.initializer()
                .handler(HeartBeatHandler::new)
                .handler(() -> this)
                .handler(() -> this.task)
                .handler(ErrorCatchHandler::new);

        try {
            bs.group(group).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true);
            bs.handler(protocol);
            var cf = bs.connect(this.address).sync();
            cf.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.task.push();
        ctx.fireChannelActive();
    }
}
