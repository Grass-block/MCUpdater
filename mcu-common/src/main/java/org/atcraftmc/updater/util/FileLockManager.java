package org.atcraftmc.updater.util;

import java.io.File;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface FileLockManager {
    Map<String, Set<String>> LOCKS = new HashMap<>();

    static boolean isDownloading(File file) {
        var locks = getLock(file);

        for (var s : locks) {
            if (s.startsWith("dl-")) {
                return true;
            }
        }
        return false;
    }

    static boolean isWriting(File file) {
        return getLock(file).contains("write");
    }

    static void tryLock(File file, SocketAddress request) {
        var locks = getLock(file);
        while (isWriting(file)) {
            Thread.yield();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        locks.add("dl-" + request.toString());
    }

    static void unlock(File file, SocketAddress request) {
        getLock(file).remove("dl-" + request.toString());
    }

    static void tryLockWrite(File file) {
        while (isDownloading(file)) {
            Thread.yield();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        getLock(file).add("write");
    }

    static void unlockWrite(File file) {
        getLock(file).remove("write");
    }


    static Set<String> getLock(File file) {
        return LOCKS.computeIfAbsent(file.getAbsolutePath(), (k) -> new HashSet<>());
    }


    static void unlockClient(SocketAddress socketAddress) {
        for (var locks : LOCKS.values()) {
            locks.remove("dl-" + socketAddress.toString());
        }
    }
}
