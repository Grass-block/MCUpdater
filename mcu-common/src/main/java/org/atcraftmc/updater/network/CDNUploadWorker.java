package org.atcraftmc.updater.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcraftmc.updater.network.handler.ErrorCatchHandler;
import org.atcraftmc.updater.network.handler.HeartBeatHandler;
import org.atcraftmc.updater.network.packet.P72_CDNUploadWorkerPayloadReceived;
import org.atcraftmc.updater.network.packet.P73_CDNUploadWorkerCancel;
import org.atcraftmc.updater.network.packet.P74_CDNUploadWorkerComplete;
import org.atcraftmc.updater.network.packet.P75_CDNUploadWorkerCompleteResponse;

import java.io.File;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CDNUploadWorker extends PacketInboundHandler implements Runnable {
    public static final Logger LOGGER = LogManager.getLogger("CDN-UploadWorker");

    private final SocketAddress address;
    private final String cdnAccount;
    private final String cdnToken;
    private final List<File> files;
    private Channel channel;
    private TransportFileSession session;

    public CDNUploadWorker(SocketAddress address, String cdnAccount, String cdnToken, Collection<File> files) {
        this.address = address;
        this.cdnAccount = cdnAccount;
        this.cdnToken = cdnToken;
        this.files = new ArrayList<>(files);
    }

    public static void start(SocketAddress address, String cdnAccount, String cdnToken, Collection<File> files) {
        var worker = new CDNUploadWorker(address, cdnAccount, cdnToken, files);
        var thread = new Thread(worker, "CDN-uploader-" + worker.hashCode());

        thread.start();
    }

    public void start() throws Exception {
        this.session = new TransportFileSession(this.files.remove(0));
        this.channel.writeAndFlush(this.session.createHeader(this.cdnAccount, this.cdnToken));
    }

    @Override
    public void run() {
        var group = new NioEventLoopGroup();
        var bs = new Bootstrap();
        var protocol = MCUProtocol.initializer()
                .handler(HeartBeatHandler::new)
                .handler(() -> this)
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
        this.channel = ctx.channel();
        this.start();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (this.session != null) {
            this.session.close();
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet p) throws Exception {
        if (p instanceof P72_CDNUploadWorkerPayloadReceived message) {
            if (this.session.getCurrentPacketId() != message.getId() - 1) {
                ctx.writeAndFlush(new P73_CDNUploadWorkerCancel());
                ctx.disconnect();
            }

            ctx.writeAndFlush(this.session.next());

            if (this.session.complete()) {
                ctx.writeAndFlush(new P74_CDNUploadWorkerComplete());
                this.session.close();
            }

            return;
        }

        if (p instanceof P75_CDNUploadWorkerCompleteResponse response) {
            if (response.isSuccess()) {
                this.files.add(0, this.session.getFile());
                this.start();//push file back, start again

                LOGGER.warn("File {} failed cdn verification, restarting upload.", this.session.getFile().getName());
                return;
            }

            if (this.files.isEmpty()) {
                LOGGER.info("No files remain to upload, shutting down.");
                ctx.disconnect();
                return;
            }

            LOGGER.warn("File {} uploaded successfully, starting next.", this.session.getFile().getName());
            this.start();
        }
    }
}
