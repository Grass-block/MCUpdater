package org.atcgroup.mcupdater.network.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.TransportFileSession;
import org.atcgroup.mcupdater.network.packet.P74_FTPComplete;
import org.atcgroup.mcupdater.network.packet.P75_FTPCompleteResponse;
import org.atcgroup.mcupdater.util.Async;
import org.atcgroup.mcupdater.util.AsyncLock;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileDownloadHandler extends PacketInboundHandler {
    public static final Logger LOGGER = LogManager.getLogger("FTPDownloadHandler");

    private final String user;
    private final String token;
    private final List<File> files;
    private Channel channel;

    private final AsyncLock writableLock = new AsyncLock();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile TransportFileSession activeSession = null;

    public FileDownloadHandler(String user, String token, List<File> files) {
        this.user = user;
        this.token = token;
        this.files = files;
    }

    public String getUser() {
        return user;
    }

    public void onFileReadStart(File file) {
    }

    public void onFileReadComplete(File file) {
    }

    public void recordProgress(File file, long p, long fp) {
    }

    private void sendFile(File file) throws Exception {
        var pid = 0L;
        if (this.active.get()) {
            throw new IllegalStateException("File transfer still active!");
        }

        this.active.set(true);
        var session = this.activeSession = new TransportFileSession(file);
        var header = session.createHeader(this.user, this.token);

        this.onFileReadStart(session.getFile());

        MCUProtocolV2.sendPacket(this.channel, header);

        LOGGER.info(
                "Starting file push {} ({} bytes in {} packets)",
                header.getFilename(),
                header.getTotalLength(),
                header.getTotalPackets()
        );

        while (!session.complete()) {
            pid++;

            var packet = session.next();

            MCUProtocolV2.sendPacket(this.channel, packet).sync();
            this.writableLock.monitor();

            if (pid % 4 == 0) {
                recordProgress(file, pid, header.getTotalPackets());
            }
        }

        MCUProtocolV2.sendPacket(this.channel, new P74_FTPComplete());
        this.onFileReadComplete(file);
        session.close();

        this.active.set(false);
    }

    public final void push() {
        Async.WORKER.submit(() -> {
            try {
                this.sendFile(this.files.remove(0));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (!ctx.channel().isWritable()) {
            this.writableLock.pause();
        } else {
            this.writableLock.resume();
        }
    }

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, Packet p) throws Exception {
        if (p instanceof P75_FTPCompleteResponse response) {
            if (!response.isSuccess()) {
                var file = this.activeSession.getFile();

                this.files.add(0, file);
                this.push();//push file back, start again

                LOGGER.warn("File {} failed verification, restarting upload.", file.getName());
                return;
            }

            if (this.files.isEmpty()) {
                ctx.pipeline().remove(this);
                LOGGER.info("No files remain to upload, shutting down.");
                this.onGlobalComplete(ctx);
                return;
            }

            LOGGER.warn("File {} sent successfully, starting next.", this.activeSession.getFile().getName());
            this.push();

            return;
        }

        ctx.fireChannelRead(p);
    }

    public void onGlobalComplete(ChannelHandlerContext ctx) {
    }
}
