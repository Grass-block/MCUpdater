package org.atcgroup.mcupdater.network.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.util.Async;
import org.atcgroup.mcupdater.network.TransportFileSession;
import org.atcgroup.mcupdater.network.packet.P72_FTPPayloadReceived;
import org.atcgroup.mcupdater.network.packet.P73_FTPCancel;
import org.atcgroup.mcupdater.network.packet.P74_FTPComplete;
import org.atcgroup.mcupdater.network.packet.P75_FTPCompleteResponse;

import java.io.File;
import java.util.List;

public class FileDownloadHandler extends PacketInboundHandler {
    public static final Logger LOGGER = LogManager.getLogger("FTPDownloadHandler");

    private final String user;
    private final String token;
    private final List<File> files;
    private Channel channel;
    private TransportFileSession session;

    public String getUser() {
        return user;
    }

    public FileDownloadHandler(String user, String token, List<File> files) {
        this.user = user;
        this.token = token;
        this.files = files;
    }

    public void onFileReadStart(File file) {
    }

    public void onFileReadComplete(File file) {
    }

    public final void push() throws Exception {
        this.session = new TransportFileSession(this.files.remove(0));

        var header = this.session.createHeader(this.user, this.token);

        Async.WORKER.submit(() -> {
            this.onFileReadStart(this.session.getFile());

            this.channel.writeAndFlush(header);
            LOGGER.info("Starting file push {} ({} bytes in {} packets)",
                        header.getFilename(),
                        header.getTotalLength(),
                        header.getTotalPackets()
            );
        });
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (this.session != null) {
            this.session.close();
        }
    }

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, Packet p) throws Exception {
        if (p instanceof P72_FTPPayloadReceived message) {
            if (this.session.getCurrentPacketId() != message.getId() - 1) {
                ctx.writeAndFlush(new P73_FTPCancel());
                ctx.disconnect();
            }

            ctx.writeAndFlush(this.session.next());

            if (this.session.complete()) {
                ctx.writeAndFlush(new P74_FTPComplete());
                this.onFileReadComplete(this.session.getFile());
                this.session.close();
            }

            return;
        }

        if (p instanceof P75_FTPCompleteResponse response) {
            if (response.isSuccess()) {
                this.files.add(0, this.session.getFile());
                this.push();//push file back, start again

                LOGGER.warn("File {} failed cdn verification, restarting upload.", this.session.getFile().getName());
                return;
            }

            if (this.files.isEmpty()) {
                ctx.pipeline().remove(this);
                LOGGER.info("No files remain to upload, shutting down.");
                this.onGlobalComplete(ctx);
                return;
            }

            LOGGER.warn("File {} uploaded successfully, starting next.", this.session.getFile().getName());
            this.push();

            return;
        }

        ctx.fireChannelRead(p);
    }

    public void onGlobalComplete(ChannelHandlerContext ctx) {
    }
}
