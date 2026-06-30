package org.atcraftmc.updater.network.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcraftmc.updater.DiffCheck;
import org.atcraftmc.updater.FilePath;
import org.atcraftmc.updater.network.MCUProtocol;
import org.atcraftmc.updater.network.packet.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;

public final class CDNUploadReceiver extends PacketInboundHandler {
    private final File file;
    private final byte[] sha256;
    private final long len;

    private long currentPacketId = 0;
    private RandomAccessFile randomAccessFile;
    private boolean done = false;

    public CDNUploadReceiver(P70_CDNUploadWorkerStart message) {
        this.file = new File(FilePath.runtime() + "/_cache" + message.getFilename());
        this.sha256 = message.getSha256();
        this.len = this.file.length();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(new P72_CDNUploadWorkerPayloadReceived(-1));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet p) throws IOException, NoSuchAlgorithmException {
        if (p instanceof P71_CDNUploadWorkerPayload payload) {
            if (this.currentPacketId != payload.getId()) {
                ctx.disconnect();
            }

            this.currentPacketId++;

            var offset = this.currentPacketId * MCUProtocol.CDN_PAYLOAD_SIZE;

            this.randomAccessFile.seek(offset);
            this.randomAccessFile.write(payload.getData());

            ctx.writeAndFlush(new P72_CDNUploadWorkerPayloadReceived(this.currentPacketId));
            return;
        }

        if (p instanceof P74_CDNUploadWorkerComplete) {
            var targetSHA256 = DiffCheck.calculateSHA256(this.file);

            if (DiffCheck.compare(targetSHA256, this.sha256)) {
                ctx.writeAndFlush(new P75_CDNUploadWorkerCompleteResponse(true));
                this.randomAccessFile.close();
                this.done = true;
                return;
            }

            ctx.writeAndFlush(new P75_CDNUploadWorkerCompleteResponse(false));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.randomAccessFile.close();
    }
}
