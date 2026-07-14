package org.atcgroup.mcupdater.network.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcraftmc.updater.network.MCUProtocol;
import org.atcgroup.mcupdater.util.DiffCheck;

import java.io.File;
import java.io.RandomAccessFile;

public abstract class FileReceiveHandler extends PacketInboundHandler {
    private File file;
    private String user;
    private RandomAccessFile randomAccessFile;
    private long currentPacketId;
    private byte[] sha256;
    private long len;

    public abstract File getFile(P70_FTPHeader header);

    public abstract void onWriteStart(File file, P70_FTPHeader header);

    public abstract void onWriteComplete(File file);

    public boolean validateConnection(String user, String token) {
        return true;
    }

    public String getCurrentUser() {
        return user;
    }

    @Override
    public final void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if (packet instanceof P70_FTPHeader header) {
            if (!validateConnection(header.getUser(), header.getToken())) {
                ctx.disconnect();
            }

            if (this.randomAccessFile != null) {
                this.randomAccessFile.close();
            }

            this.file = this.getFile(header);
            this.randomAccessFile = new RandomAccessFile(this.file, "rw");
            this.currentPacketId = 0;
            this.sha256 = header.getSha256();
            this.len = this.file.length();
            this.user = header.getUser();

            this.onWriteStart(this.file, header);

            ctx.writeAndFlush(new P72_FTPPayloadReceived(-1));
            return;
        }

        if (packet instanceof P71_FTPPayload payload) {
            if (this.currentPacketId != payload.getId()) {
                ctx.disconnect();
            }

            this.currentPacketId++;

            var offset = this.currentPacketId * MCUProtocol.CDN_PAYLOAD_SIZE;

            this.randomAccessFile.seek(offset);
            this.randomAccessFile.write(payload.getData());

            ctx.writeAndFlush(new P72_FTPPayloadReceived(this.currentPacketId));
            return;
        }

        if (packet instanceof P74_FTPComplete) {
            var targetSHA256 = DiffCheck.calculateSHA256(this.file);

            if (DiffCheck.compare(targetSHA256, this.sha256) && this.len == this.randomAccessFile.length()) {
                ctx.writeAndFlush(new P75_FTPCompleteResponse(true));
                this.randomAccessFile.close();

                this.onWriteComplete(this.file);
                return;
            }

            ctx.writeAndFlush(new P75_FTPCompleteResponse(false));
            return;
        }

        ctx.fireChannelRead(packet);
    }
}
