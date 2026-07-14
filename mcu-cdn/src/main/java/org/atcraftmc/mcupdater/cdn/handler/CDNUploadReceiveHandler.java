package org.atcraftmc.mcupdater.cdn.handler;

import org.atcraftmc.mcupdater.cdn.FileStatusManager;
import org.atcgroup.mcupdater.network.handler.FileReceiveHandler;
import org.atcgroup.mcupdater.network.packet.P70_FTPHeader;
import org.atcraftmc.updater.util.FilePath;

import java.io.File;

public final class CDNUploadReceiveHandler extends FileReceiveHandler {
    private final FileStatusManager fileManager;

    public CDNUploadReceiveHandler(FileStatusManager fileManager) {
        this.fileManager = fileManager;
    }

    @Override
    public File getFile(P70_FTPHeader header) {
        return new File(FilePath.runtime() + "/" + header.getUser() + "/_cache" + header.getFilename());
    }

    @Override
    public void onWriteStart(File file, P70_FTPHeader header) {

    }

    @Override
    public void onWriteComplete(File file) {
        var dest = new File(FilePath.runtime() + "/" + getCurrentUser() + "/" + file.getName());
        this.fileManager.queueFileMerge(file, dest);
    }
}
