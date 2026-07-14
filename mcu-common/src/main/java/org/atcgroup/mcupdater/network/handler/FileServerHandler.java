package org.atcgroup.mcupdater.network.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.commons.file.FilePath;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.util.DiffCheck;
import org.atcgroup.mcupdater.util.FileChecksumManager;
import org.atcgroup.mcupdater.network.packet.P30_FileDownloadRequest;
import org.atcgroup.mcupdater.network.packet.P31_FileDownloadStart;
import org.atcgroup.mcupdater.network.packet.P32_FileDownloadComplete;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

public final class FileServerHandler extends PacketInboundHandler {
    private final FilePath root;
    private final FileChecksumManager checksumManager;

    public FileServerHandler(FilePath root) {
        this.root = root;
        this.checksumManager = new FileChecksumManager(root);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if (packet instanceof P30_FileDownloadRequest request) {
            var files = request.getFiles();
            var tid = UUID.randomUUID().toString();
            var actualFiles = new ArrayList<File>();

            for (var channel : files.keySet()) {
                var folder = this.root.append(channel);

                for (var e : files.get(channel).entrySet()) {
                    var name = e.getKey();
                    var hash = e.getValue();
                    var fileP = folder.append(name);
                    var file = fileP.file();

                    if (!file.exists() || file.length() == 0) {
                        continue;
                    }

                    if (!DiffCheck.compare(hash, this.checksumManager.getFileChecksum(channel, name))) {
                        continue;
                    }

                    actualFiles.add(file);
                }
            }

            ctx.writeAndFlush(new P31_FileDownloadStart(tid, files));

            if (!actualFiles.isEmpty()) {
                var task = new FileDownloadHandler("__client__", "__client__", actualFiles) {
                    @Override
                    public void onGlobalComplete(ChannelHandlerContext ctx) {
                        ctx.writeAndFlush(new P32_FileDownloadComplete(tid));
                    }
                };
                ctx.pipeline().addLast(task);
                task.push();
            } else {
                ctx.writeAndFlush(new P32_FileDownloadComplete(tid));
            }

            return;
        }

        ctx.fireChannelRead(packet);
    }
}
