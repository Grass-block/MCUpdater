package org.atcgroup.mcupdater.server.analyzer;

import org.atcgroup.mcupdater.data.VersionInfo;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public interface VersionFileAnalyzer {
    ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(16);

    default void handleFiles(Set<SourceFileInfo> files, VersionInfo info) {
        var toRemove = new HashSet<SourceFileInfo>();
        var waiting = new HashSet<Future<?>>();

        for (var file : files) {
            waiting.add(EXECUTOR_SERVICE.submit(() -> {
                if (handleNewFile(file.relPath(), file.file(), file.sha1(), info)) {
                    toRemove.add(file);
                }
            }));
        }

        for (var future : waiting) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        for (var rem : toRemove) {
            files.remove(rem);
        }
    }


    default boolean handleNewFile(String relPath, File file, byte[] sha1, VersionInfo info) {
        return false;
    }

    record SourceFileInfo(String relPath, File file, byte[] sha1) {
    }
}
