package org.atcgroup.mcupdater.network.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.commons.file.FilePath;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.data.DownloadFileList;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.P30_FileDownloadRequest;
import org.atcgroup.mcupdater.network.packet.P31_FileDownloadStart;
import org.atcgroup.mcupdater.network.packet.P32_FileDownloadComplete;
import org.atcgroup.mcupdater.util.DiffCheck;
import org.atcgroup.mcupdater.util.FileChecksumManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileServerHandler extends PacketInboundHandler {
    private final FilePath root;
    private final FileChecksumManager checksumManager;

    public FileServerHandler(FilePath root) {
        this.root = root;
        this.checksumManager = new FileChecksumManager(root);
    }

    public FileServerHandler(FileChecksumManager checksumManager) {
        this.root = checksumManager.getRoot();
        this.checksumManager = checksumManager;
    }

    public FileDownloadHandler createDownloadTask(String user, String token, List<File> files, String tid) {
        return new FileDownloadHandler(user, token, files) {
            @Override
            public void onGlobalComplete(ChannelHandlerContext ctx) {
                MCUProtocolV2.sendPacket(ctx, new P32_FileDownloadComplete(tid));
            }
        };
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if (packet instanceof P30_FileDownloadRequest request) {
            var files = request.getFiles();
            var tid = UUID.randomUUID().toString();
            var actualFiles = new ArrayList<File>();
            var failedFiles = new DownloadFileList();

            for (var channel : files.keySet()) {
                for (var e : files.get(channel).entrySet()) {
                    var name = e.getKey();
                    var hash = e.getValue();
                    var fileP = this.root.append(name);
                    var file = fileP.file();

                    if (!file.exists() || file.length() == 0) {
                        failedFiles.addFile(channel, name, hash);
                        continue;
                    }

                    if (!DiffCheck.compare(hash, this.checksumManager.getFileChecksum(name))) {
                        failedFiles.addFile(channel, name, hash);
                        continue;
                    }

                    actualFiles.add(file);
                }
            }

            MCUProtocolV2.sendPacket(ctx, new P31_FileDownloadStart(tid, failedFiles));

            if (!actualFiles.isEmpty()) {
                var task = createDownloadTask("__client__", "__client__", actualFiles, tid);
                ctx.pipeline().addLast(task);
                task.push();
            } else {
                MCUProtocolV2.sendPacket(ctx, new P32_FileDownloadComplete(tid));
            }

            return;
        }

        ctx.fireChannelRead(packet);
    }
}
