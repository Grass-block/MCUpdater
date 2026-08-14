package org.atcraftmc.mcupdater.cdn.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.commons.file.FilePath;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.FileDownloadHandler;
import org.atcgroup.mcupdater.network.handler.FileServerHandler;
import org.atcgroup.mcupdater.network.packet.P32_FileDownloadComplete;
import org.atcraftmc.mcupdater.cdn.FileStatusManager;

import java.io.File;
import java.util.List;

public final class CDNFileServerHandler extends FileServerHandler {
    private final FileStatusManager fileStatusManager;

    public CDNFileServerHandler(FilePath root, FileStatusManager fileStatusManager) {
        super(root);
        this.fileStatusManager = fileStatusManager;
    }

    @Override
    public FileDownloadHandler createDownloadTask(String user, String token, List<File> files, String tid) {
        return new CDNLockableDownloadHandler(user, token, files, this.fileStatusManager) {
            @Override
            public void onGlobalComplete(ChannelHandlerContext ctx) {
                MCUProtocolV2.sendPacket(ctx, new P32_FileDownloadComplete(tid));
            }
        };
    }

    public static class CDNLockableDownloadHandler extends FileDownloadHandler {
        private final FileStatusManager fileManager;

        public CDNLockableDownloadHandler(String user, String token, List<File> files, FileStatusManager fileManager) {
            super(user, token, files);
            this.fileManager = fileManager;
        }

        @Override
        public void onFileReadStart(File file) {
            this.fileManager.syncForRead(file);
            this.fileManager.addReadLock(file, getUser());
        }

        @Override
        public void onFileReadComplete(File file) {
            super.onFileReadComplete(file);
            this.fileManager.removeReadLock(file, getUser());
        }
    }
}
