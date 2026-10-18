package org.atcgroup.mcupdater.server.cdn;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.commons.file.FilePath;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.network.ErrorCatchHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.FileDownloadHandler;
import org.atcgroup.mcupdater.network.packet.P40_CDNFileCheckRequest;
import org.atcgroup.mcupdater.network.packet.P41_CDNFileCheckResult;
import org.atcgroup.mcupdater.server.MCUpdaterServer;
import org.atcgroup.mcupdater.util.I18n;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.atcgroup.mcupdater.server.MCUpdaterServer.LOGGER;

public final class CDNStorageHandler implements CDNHandler {
    @Override
    public void sync(FileRepository repository) {
        var session = MCUpdaterServer.instance().createSession();

        if (CDNUploadWorker.WORKING.get()) {
            LOGGER.info(I18n.message("console.cdn_upload.active"));
            return;
        }
        if (!session.hasCDNInfo()) {
            LOGGER.info(I18n.message("console.cdn_upload.missing"));
            return;
        }

        var host = session.getCdnHost();
        var port = session.getCdnPort();
        var account = session.getCdnRepository();
        var token = MCUpdaterServer.instance().config().getString("cdn-service.mcu-cdn.access-token");

        var addr = new InetSocketAddress(host, port);

        CDNUploadWorker.start(repository, addr, account, token);
    }

    @Override
    public void handle(UpdateHeader data, FileRepository repository) {
    }

    public static final class CDNUploadWorker extends PacketInboundHandler implements Runnable {
        public static final Logger LOGGER = LogManager.getLogger("CDN-UploadWorker");
        public static final AtomicBoolean WORKING = new AtomicBoolean(false);

        private final FileRepository target;
        private final SocketAddress address;
        private final String cdnRepository;
        private final String cdnToken;

        public CDNUploadWorker(FileRepository target, SocketAddress address, String cdnRepository, String cdnToken) {
            this.target = target;
            this.address = address;
            this.cdnRepository = cdnRepository;
            this.cdnToken = cdnToken;
        }

        public static void start(FileRepository target, SocketAddress address, String cdnAccount, String cdnToken) {
            var worker = new CDNUploadWorker(target, address, cdnAccount, cdnToken);
            var thread = new Thread(worker, "CDN-uploader-" + worker.hashCode());

            WORKING.set(true);

            thread.start();
        }

        @Override
        public void run() {
            var group = new NioEventLoopGroup();
            var bs = new Bootstrap();
            bs.option(ChannelOption.SO_SNDBUF, 1048576);
            bs.option(ChannelOption.SO_RCVBUF, 1048576);

            var protocol = MCUProtocolV2.initializer()
                    //.handler(HeartBeatHandler::new)
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
                WORKING.set(false);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            var req = new P40_CDNFileCheckRequest(this.cdnRepository);

            Arrays.stream(Objects.requireNonNull(FilePath.RUNTIME.append("packs").file().listFiles()))
                    .filter((f) -> !f.getName().endsWith(".sum"))
                    .filter((f) -> !f.getName().endsWith(".meta"))
                    .filter(File::isFile)
                    .map(File::getName)
                    .forEach((s) -> req.addHashInfo(s, this.target.getFileInfo(s).getSha1Raw()));

            MCUProtocolV2.sendPacket(ctx, req);
            ctx.fireChannelActive();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
            if (packet instanceof P41_CDNFileCheckResult r) {
                var f = r.getNeedUpdate().stream().map((s) -> FilePath.RUNTIME.append("packs").append(s).file()).collect(Collectors.toList());

                if (f.isEmpty()) {
                    LOGGER.info(I18n.message("console.cdn_upload.no-remain"));
                    return;
                }

                var l = new FileDownloadHandler(this.cdnRepository, this.cdnToken, f) {
                    @Override
                    public void onFileReadStart(File file) {
                        LOGGER.info("Transport Started: {}", file.getName());
                    }

                    @Override
                    public void recordProgress(File file, long p, long fp) {
                        LOGGER.info("Transport Progress: {} slice: {}/{}", file.getName(), p, fp);
                    }

                    @Override
                    public void onFileReadComplete(File file) {
                        LOGGER.info("Transport Completed: {}", file.getName());
                    }

                    @Override
                    public void onGlobalComplete(ChannelHandlerContext ctx) {
                        super.onGlobalComplete(ctx);
                        ctx.disconnect();
                    }
                };
                ctx.channel().pipeline().addLast(l);
                l.push();
                return;
            }

            ctx.fireChannelRead(packet);
        }
    }
}