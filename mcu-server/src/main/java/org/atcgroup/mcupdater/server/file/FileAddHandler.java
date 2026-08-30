package org.atcgroup.mcupdater.server.file;

import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.util.DiffCheck;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public interface FileAddHandler {
    ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(16);
    List<FileAddHandler> PIPELINE = new ArrayList<>();

    static void init(ConfigurationSection config) {
        for (var id : config.getStringList("order")) {
            var dom = config.getConfigurationSection(id);

            PIPELINE.add(switch (id) {
                case "modrinth" -> new ModrinthModAnalyzer(dom);
                case "fallback" -> new PackCompressHandler(dom);
                default -> throw new IllegalStateException("Unexpected FileAnalyzer ID: " + id);
            });
        }
    }

    static void iterateFiles(Map<String, File> files, VersionInfo info) {
        var futures = new ArrayList<Future<?>>();
        var counter = new AtomicInteger();

        for (var handler : PIPELINE) {
            handler.onStart(info);
        }

        for (var path : files.keySet()) {
            var future = EXECUTOR_SERVICE.submit(() -> {
                var file = files.get(path);
                var sha1 = DiffCheck.calculateSHA1(file);

                for (var handler : PIPELINE) {
                    if (handler.handleNewFile(path, file, sha1, info)) {
                        break;
                    }
                }

                counter.incrementAndGet();
            });

            futures.add(future);
        }

        for (var future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        for (var handler : PIPELINE) {
            handler.onScanComplete(info);
        }
    }

    boolean handleNewFile(String relPath, File file, byte[] sha1, VersionInfo info);

    default void onScanComplete(VersionInfo info) {

    }

    default void onStart(VersionInfo info) {

    }
}
