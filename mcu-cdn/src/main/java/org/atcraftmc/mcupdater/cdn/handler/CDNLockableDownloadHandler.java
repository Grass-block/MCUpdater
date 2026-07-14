package org.atcraftmc.mcupdater.cdn.handler;

import org.atcraftmc.mcupdater.cdn.FileStatusManager;
import org.atcgroup.mcupdater.network.handler.FileDownloadHandler;

import java.io.File;
import java.util.List;

public final class CDNLockableDownloadHandler extends FileDownloadHandler {
    private final FileStatusManager fileManager;

    public CDNLockableDownloadHandler(String user, String token, List<File> files, FileStatusManager fileManager) {
        super(user, token, files);
        this.fileManager = fileManager;
    }

    @Override
    public void onFileReadStart(File file) {
        this.fileManager.syncForRead(file);
        this.fileManager.addReadLock(file,getUser());
    }

    @Override
    public void onFileReadComplete(File file) {
        super.onFileReadComplete(file);
        this.fileManager.removeReadLock(file,getUser());
    }
}
