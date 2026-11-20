package org.atcgroup.mcupdater.server.v4.diff;

import me.gb2022.commons.file.FilePath;
import me.gb2022.commons.math.SHA;
import org.atcgroup.mcupdater.server.file.FileModifyStatus;
import org.atcgroup.mcupdater.server.v4.data.DiffFileInfo;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class FileStatusManager {
    private final Map<String, DiffFileInfo> files = new HashMap<>(); //path:info
    private final FilePath statusStorageRoot;

    public FileStatusManager(FilePath statusStorageRoot) {
        this.statusStorageRoot = statusStorageRoot;
    }

    private File getStatusFile(String relPath) {
        var key = SHA.getSHA1(relPath, false);
        return this.statusStorageRoot.append(key.substring(0, 2)).append(key).file();
    }

    public FileModifyStatus checkFile(String relPath) {
        return FileModifyStatus.NONE;//todo
    }
}
