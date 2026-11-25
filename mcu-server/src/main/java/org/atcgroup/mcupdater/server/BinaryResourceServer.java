package org.atcgroup.mcupdater.server;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.FileDownloadHandler;
import org.atcgroup.mcupdater.network.packet.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BinaryResourceServer extends PacketInboundHandler {
    public static final Logger LOGGER = LogManager.getLogger("BinaryResourceServer");

    private final CASResourceStorage storage;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final int httpFileDownloadServicePort;
    private final String httpFileDownloadRepoName;

    public BinaryResourceServer(CASResourceStorage storage, int httpFileDownloadServicePort, String httpFileRepoName) {
        this.storage = storage;
        this.httpFileDownloadServicePort = httpFileDownloadServicePort;
        this.httpFileDownloadRepoName = httpFileRepoName;
    }

    public FileDownloadHandler createDownloadTask(String user, String token, List<File> files) {
        return new FileDownloadTask(user, token, files);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if (packet instanceof P34_UpgradeFileDownloadComplete) {
            MCUProtocolV2.sendPacket(ctx, new P32_FileDownloadComplete());
        }

        if (packet instanceof P30_FileDownloadRequest request) {
            if (!this.active.compareAndSet(false, true)) {
                LOGGER.warn("Rejected overlapping download request from {}, a transfer is already in progress.", ctx.channel()
                                                                                                                    .remoteAddress());
                return;
            }

            var files = request.getFiles();
            var tid = UUID.randomUUID().toString();
            var actualFiles = new ArrayList<File>();
            var filesToUpgradeDownload = new HashMap<String, String>();
            var failedFiles = new HashMap<String, String>();

            for (var resourceId : files.keySet()) {
                var hash = files.get(resourceId);
                var file = this.storage.getResource(hash);

                if (file.isEmpty()) {
                    failedFiles.put(resourceId, hash);
                    continue;
                }

                actualFiles.add(file.get());
                filesToUpgradeDownload.put(resourceId, hash);
            }

            MCUProtocolV2.sendPacket(ctx, new P31_FileDownloadStart(tid, failedFiles));

            if (actualFiles.isEmpty()) {
                MCUProtocolV2.sendPacket(ctx, new P32_FileDownloadComplete());
                this.active.set(false);
                return;
            }

            if (this.httpFileDownloadServicePort != -1) {
                MCUProtocolV2.sendPacket(ctx, new P33_FTPUpgradeToHttp(this.httpFileDownloadServicePort, this.httpFileDownloadRepoName, filesToUpgradeDownload));
                this.active.set(false);
                return;
            }

            var user = String.valueOf(ctx.channel().remoteAddress());
            var task = createDownloadTask(user, user, actualFiles);
            ctx.pipeline().addLast(task);
            task.push();

            return;
        }

        ctx.fireChannelRead(packet);
    }


    private final class FileDownloadTask extends FileDownloadHandler {
        public FileDownloadTask(String user, String token, List<File> files) {
            super(user, token, files);
        }

        @Override
        public void onGlobalComplete(ChannelHandlerContext ctx) {
            BinaryResourceServer.this.active.set(false);
            MCUProtocolV2.sendPacket(ctx, new P32_FileDownloadComplete());
        }
    }
}
