package org.atcgroup.mcupdater.client;

import me.gb2022.commons.file.FilePath;

public interface ClientFilePath {
    FilePath UPDATER = FilePath.RUNTIME.append(".updater");
    FilePath VERSION_INFO = UPDATER.append("versions.dat");
    FilePath CACHE = UPDATER.append("cache");
}
