package org.atcgroup.mcupdater;

import me.gb2022.commons.file.FilePath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.data.FileInfo;
import org.atcgroup.mcupdater.util.AsyncLock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FileRepository {
    public static final Logger LOGGER = LogManager.getLogger("FileRepository");

    private final Map<String, FileInfo> fileInfos = new HashMap<>();
    public final Map<String, AsyncLock> writeLocks = new ConcurrentHashMap<>();
    public final Map<String, AsyncLock> readLocks = new ConcurrentHashMap<>();
    private final Map<String, FileMergeInfo> mergeQueue = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> readLockLists = new ConcurrentHashMap<>();
    private final FilePath rootPath;

    public FileRepository(FilePath rootPath) {
        this.rootPath = rootPath;
    }

    public FileInfo getFileInfo(String name) {
        if (this.fileInfos.containsKey(name)) {
            return this.fileInfos.get(name);
        }

        var file = this.rootPath.append(name).file();
        var infoFile = this.getInfoStorageFile(name);

        if (!file.exists() || file.isDirectory()) {
            return null;
        }

        if (!infoFile.exists() || infoFile.length() == 0) {
            return regenerateFileInfo(name);
        }

        try (var in = new FileInputStream(infoFile)) {
            var dom = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("/");
            var len = Integer.parseInt(dom[0]);
            var sha1 = dom[1];
            var sha256 = dom[2];

            if (len != file.length()) {
                return regenerateFileInfo(name);
            }

            var i = new FileInfo(name, len, sha1, sha256);
            this.fileInfos.put(name, i);
            return i;
        } catch (Exception e) {
            return regenerateFileInfo(name);
        }
    }

    public void updateFileInfo(String name) {
        this.fileInfos.put(name, regenerateFileInfo(name));
    }

    public void addWriteLock(File file) {
        this.syncForWrite(file);

        this.writeLocks.computeIfAbsent(file.getAbsolutePath(), (k) -> new AsyncLock()).pause();
    }

    public void removeWriteLock(File file) {
        if (!this.writeLocks.containsKey(file.getAbsolutePath())) {
            return;
        }

        this.writeLocks.get(file.getAbsolutePath()).resume();
    }

    public void syncForWrite(File file) {
        if (this.readLocks.containsKey(file.getAbsolutePath())) {
            this.readLocks.get(file.getAbsolutePath()).monitor();
        }
    }

    public void addReadLock(File file, String lock) {
        this.syncForRead(file);

        this.readLockLists.computeIfAbsent(file.getAbsolutePath(), k -> new HashSet<>()).add(lock);
        this.readLocks.computeIfAbsent(file.getAbsolutePath(), (k) -> new AsyncLock()).pause();
    }

    public void removeReadLock(File file, String lock) {
        this.readLockLists.computeIfAbsent(file.getAbsolutePath(), k -> new HashSet<>()).remove(lock);

        if (this.readLocks.containsKey(file.getAbsolutePath())) {
            this.readLocks.get(file.getAbsolutePath()).resume();
        }

        if (isAvailableForWrite(file)) {
            if (!this.mergeQueue.containsKey(file.getAbsolutePath())) {
                return;
            }

            var data = this.mergeQueue.remove(file.getAbsolutePath());

            this.mergeFile(data);
        }
    }

    public void removeReadLocks(String lock) {
        for (var root : this.readLockLists.keySet()) {
            removeReadLock(new File(root), lock);
        }
    }

    public void syncForRead(File file) {
        if (this.writeLocks.containsKey(file.getAbsolutePath())) {
            this.writeLocks.get(file.getAbsolutePath()).monitor();
        }
    }

    public void queueFileMerge(String repo, File dest, File temp) {
        var info = new FileMergeInfo(repo, dest, temp);

        if (this.isAvailableForWrite(dest)) {
            this.mergeFile(info);
            return;
        }
        this.mergeQueue.put(dest.getAbsolutePath(), info);
    }

    public boolean isAvailableForWrite(File file) {
        if (!this.readLocks.containsKey(file.getAbsolutePath())) {
            return true;
        }

        return !this.readLocks.get(file.getAbsolutePath()).paused();
    }

    public boolean isAvailableForRead(File file) {
        if (!this.writeLocks.containsKey(file.getAbsolutePath())) {
            return true;
        }

        return !this.writeLocks.get(file.getAbsolutePath()).paused();
    }


    private File getInfoStorageFile(String name) {
        return this.rootPath.append(name + ".meta").file();
    }

    private FileInfo regenerateFileInfo(String name) {
        var file = this.rootPath.append(name).file();
        var infoFile = this.getInfoStorageFile(name);
        var info = FileInfo.calculateOf(file);

        if (info == null) {
            return null;
        }

        try (var out = new FileOutputStream(infoFile)) {
            out.write((info.getLength() + "/" + info.getSha1() + "/" + info.getSha256()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return info;
    }

    private void mergeFile(FileMergeInfo info) {
        this.addWriteLock(info.dest());

        info.dest().getParentFile().mkdirs();
        try {
            info.dest().createNewFile();

            var i = Path.of(info.temp().toURI());
            var o = Path.of(info.dest().toURI());
            Files.copy(i, o, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //merge
        this.removeWriteLock(info.dest());

        if (!info.temp().delete()) {
            LOGGER.warn("Failed to delete temp file {}", info.temp().getName());
        }

        updateFileInfo(info.dest().getName());
    }

    public File getFile(String name) {
        return this.rootPath.append(name).file();
    }

    public Path getRoot() {
        return this.rootPath.file().toPath();
    }


    record FileMergeInfo(String repo, File dest, File temp) {
    }
}
