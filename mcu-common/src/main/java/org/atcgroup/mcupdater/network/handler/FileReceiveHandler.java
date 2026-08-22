package org.atcgroup.mcupdater.network.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.util.DiffCheck;

import java.io.File;
import java.io.RandomAccessFile;

public abstract class FileReceiveHandler extends PacketInboundHandler {
    private File file;
    private String user;
    private RandomAccessFile randomAccessFile;
    private byte[] sha256;
    private long len;

    public abstract File getFile(P70_FTPHeader header);

    public abstract void onWriteStart(File file, P70_FTPHeader header);

    public abstract void onWriteComplete(String user, File file);

    public void onProcess(String name, long received, long total) {

    }

    public boolean validateConnection(String user, String token) {
        return true;
    }

    public String getCurrentUser() {
        return user;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("WTF?");
    }

    @Override
    public final void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        System.out.println(packet);

        if (packet instanceof P70_FTPHeader header) {
            if (!validateConnection(header.getUser(), header.getToken())) {
                ctx.disconnect();
            }

            if (this.randomAccessFile != null) {
                this.randomAccessFile.close();
            }

            this.file = this.getFile(header);

            this.file.getParentFile().mkdirs();

            if (!this.file.exists()) {
                this.file.createNewFile();
            }

            this.randomAccessFile = new RandomAccessFile(this.file, "rw");
            this.randomAccessFile.setLength(0);
            this.sha256 = header.getSha256();
            this.len = header.getTotalLength();
            this.user = header.getUser();

            this.onWriteStart(this.file, header);

            MCUProtocolV2.sendPacket(ctx, new P72_FTPPayloadReceived(-1));
            return;
        }

        if (packet instanceof P71_FTPPayload payload) {
            var offset = payload.getId() * MCUProtocolV2.CDN_PAYLOAD_SIZE;
            var data = payload.getData();

            this.randomAccessFile.seek(offset);
            this.randomAccessFile.write(data);

            this.onProcess(this.file.getName(), offset + data.length, this.len);

            MCUProtocolV2.sendPacket(ctx, new P72_FTPPayloadReceived(payload.getId()));
            return;
        }

        if (packet instanceof P74_FTPComplete) {
            var targetSHA256 = DiffCheck.calculateSHA256(this.file);

            if (this.len != this.randomAccessFile.length()) {
                System.out.println(this.file.getName() + " failed - length mismatch - " + randomAccessFile.length() + "/" + this.len);
                MCUProtocolV2.sendPacket(ctx, new P75_FTPCompleteResponse(false));
                return;
            }

            if (!DiffCheck.compare(targetSHA256, this.sha256)) {
                System.out.println(this.file.getName() + " failed - SHA256 mismatch!");
                MCUProtocolV2.sendPacket(ctx, new P75_FTPCompleteResponse(false));
                return;
            }

            MCUProtocolV2.sendPacket(ctx, new P75_FTPCompleteResponse(true));
            this.randomAccessFile.close();

            this.onWriteComplete(this.user,this.file);
            return;
        }

        ctx.fireChannelRead(packet);
    }
}
