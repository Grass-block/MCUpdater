package org.atcraftmc.mcupdater.cdn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.util.AsyncLock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

        var buffer = new byte[131072];
        var offset = 0;
        var len = 0;

        try (var in = new FileInputStream(info.temp()); var out = new FileOutputStream(info.dest())) {
            while ((len = in.read(buffer)) != 0) {
                out.write(buffer, offset, len);
                offset += len;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //merge
        this.removeWriteLock(info.dest());

        if (!info.temp().delete()) {
            LOGGER.warn("Failed to delete temp file {}", info.temp().getName());
        }
    }

    public void queueFileMerge(File dest, File temp) {
        var info = new FileMergeInfo(dest, temp);

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

    record FileMergeInfo(File dest, File temp) {
    }
}
