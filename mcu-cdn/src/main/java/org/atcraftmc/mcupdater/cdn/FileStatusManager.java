package org.atcraftmc.mcupdater.cdn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.util.AsyncLock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FileStatusManager {
    public static final Logger LOGGER = LogManager.getLogger("FileStatusManager");

    public final Map<String, AsyncLock> writeLocks = new ConcurrentHashMap<>();
    public final Map<String, AsyncLock> readLocks = new ConcurrentHashMap<>();
    private final Map<String, FileMergeInfo> mergeQueue = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> readLockLists = new ConcurrentHashMap<>();

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

        MCUpdaterCDNServer.INSTANCE.getRepoChecksumManager(info.repo()).updateFileChecksum(info.dest().getName());
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

    record FileMergeInfo(String repo, File dest, File temp) {
    }
}
