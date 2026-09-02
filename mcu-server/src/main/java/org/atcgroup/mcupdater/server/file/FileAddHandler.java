package org.atcgroup.mcupdater.server.file;

import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.util.DiffCheck;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public interface FileAddHandler {
    ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(16);
    List<FileAddHandler> PIPELINE = new ArrayList<>();

    static void init(ConfigurationSection config) {
        for (var id : config.getStringList("order")) {
            var dom = config.getConfigurationSection(id);

            PIPELINE.add(switch (id) {
                case "modrinth" -> new ModrinthModAnalyzer(dom);
                case "fallback" -> new PackCompressHandler(dom);
                case "curseforge" -> new CurseForgeModAnalyzer(dom);
                default -> throw new IllegalStateException("Unexpected FileAnalyzer ID: " + id);
            });
        }
    }

    static void iterateFiles(Map<String, File> files, VersionInfo info) {
        var data = new HashSet<SourceFileInfo>();

        for (var path : files.keySet()) {
            var file = files.get(path);
            var sha1 = DiffCheck.calculateSHA1(file);

            data.add(new SourceFileInfo(path, file, sha1));
        }

        for (var handler : PIPELINE) {
            handler.handleFiles(data, info);
        }
    }


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
