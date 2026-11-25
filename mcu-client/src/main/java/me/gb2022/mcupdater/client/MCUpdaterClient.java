package me.gb2022.mcupdater.client;

import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.UpdateHeader;
import me.gb2022.mcupdater.client.network.AssetDownloadResolver;
import me.gb2022.mcupdater.client.network.ClientConnector;
import me.gb2022.mcupdater.client.ui.ClientUI;
import me.gb2022.mcupdater.client.util.ClientFilePath;
import me.gb2022.mcupdater.client.util.TaskBatch;
import me.gb2022.mcupdater.data.ChannelMetadata;
import me.gb2022.mcupdater.protocol.P02_ServerHandshake;
import me.gb2022.mcupdater.protocol.P10_ChannelHeaderRequest;
import me.gb2022.mcupdater.protocol.P12_UpdateRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.util.AsyncLock;
import org.atcgroup.mcupdater.util.FilePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MCUpdaterClient {
    public static final Logger LOGGER = LogManager.getLogger("MCU/Client");

    private final ClientVersionRecord info = new ClientVersionRecord();
    private final ClientConnector connector = new ClientConnector(this);
    private final AsyncLock lock = new AsyncLock();
    private final ClientConfig config = new ClientConfig();
    private final ClientUI clientUi;
    private volatile P02_ServerHandshake serverInfo;
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "mcu:update");
        thread.setDaemon(true);
        return thread;
    });

    public MCUpdaterClient(ClientUI clientUi) {
        this.clientUi = clientUi;
        this.clientUi._setContext(this);
    }

    public void start() {
        this.lock.pause();
        this.clientUi.init();

        new Thread(() -> {
            if (!this.config.load()) {
                this.handleException(ClientError.CONFIG, new IllegalArgumentException("FAILED TO LOAD CONFIG!"));
                return;
            }

            this.clientUi.handleClientConfigLoaded(this.config);

            this.info.load();
            this.connector.run(this.config.service());
        }, "mcu:main").start();

        this.lock.monitor();
    }

    public void dispose() {
        this.clientUi.dispose();
        this.connector.shutdown();
        this.lock.resume();
    }

    public void handleException(ClientError error, Exception e) {
        this.clientUi.handleClientError(error, e);

        LOGGER.error("An error occurred: {}", e.toString());
        LOGGER.catching(e);
    }

    public void handleServerConnected(P02_ServerHandshake handshake) {
        this.serverInfo = handshake;
        if (this.clientUi.handleServerConnected()) {
            requestUpdate();
        }
    }

    public void requestUpdate() {
        this.connector.write(new P12_UpdateRequest(this.info.getLocalVersions()));
        this.clientUi.handleUpdateStarted();
    }

    public void requestConfig() {
        this.connector.write(new P10_ChannelHeaderRequest());
    }

    public void handleConfig(Set<ChannelMetadata> metadata){
        this.clientUi.handleConfiguration(metadata);
    }



    //======================================================
    // Update handling
    //======================================================
    public void handleVersionData(UpdateHeader headers) {
        this.updateExecutor.execute(() -> {
            try {
                var batch = new TaskBatch();
                AssetDownloadResolver.resolveAsset(this, headers.getAssets().values(), batch);
                batch.emit(this.clientUi);
            } catch (Exception error) {
                this.handleException(ClientError.NETWORK, error);
                return;
            }
            try {
                commitChanges(headers);
            } catch (Exception error) {
                this.handleException(ClientError.OTHER, error);
            }
        });
    }

    private void commitChanges(UpdateHeader header) {
        // Validate delete/update paths and resource keys before the first filesystem change.
        header.getDelete().forEach(MCUpdaterClient::resolveRuntimePath);
        header.getUpdate().keySet().forEach(MCUpdaterClient::resolveRuntimePath);
        header.getUpdate().values().forEach(MCUpdaterClient::resolveCachePath);
        header.getExtract().forEach(MCUpdaterClient::resolveCachePath);
        var batch = new TaskBatch();

        if (!header.getDelete().isEmpty()) {
            batch.createMainTask((task) -> deleteFiles(header.getDelete(), task));
        }

        if (!header.getExtract().isEmpty()) {
            batch.createMainTask((task -> expandFiles(header.getExtract(), task)));
        }

        if (!header.getUpdate().isEmpty()) {
            batch.createMainTask((task) -> updateFiles(header.getUpdate(), task));
        }

        batch.emit(this.clientUi);

        completeUpdate(header);
    }

    private void completeUpdate(UpdateHeader header) {
        for (var v : header.getVersions()) {
            this.info.setTime(v.getChannel(), v.getHeadTimeStamp());
        }

        this.info.save();
        this.connector.shutdown();
        this.clientUi.handleUpdateComplete(header);

        this.lock.resume();
    }

    private static void deleteFiles(Set<String> delete, TaskBatch.Task callback) {
        var counter = 0;
        for (var s : delete) {
            counter++;
            var a = delete.size();
            var p = (int) (counter / (float) a * 100);
            var title = "正在移除旧版文件 [%s/%s - %s%%] : %s".formatted(counter, a, p, s);

            callback.reportProgress(title, counter, a);

            var file = resolveRuntimePath(s);

            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }

            var root = FilePath.RUNTIME.file().toPath().toAbsolutePath().normalize();
            var backupRoot = ClientFilePath.UPDATER.append("removed").file().toPath().toAbsolutePath().normalize();
            var target = resolveInside(backupRoot, root.relativize(file).toString());

            try {
                Files.createDirectories(target.getParent());
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void expandFiles(Set<String> extract, TaskBatch.Task callback) {
        var counter = 1;
        for (var s : extract) {
            var file = resolveCachePath(s).toFile();
            var a0 = extract.size();

            int finalCounter = counter;
            PatchFile.unzip(file, FilePaths.runtime(), (c, a) -> {
                var title = "正在解压资源包 第(%s/%s个) [%s/%s]".formatted(finalCounter, a0, c, a);
                callback.reportProgress(title, finalCounter, a);
            });

            counter++;
        }
    }

    private static void updateFiles(Map<String, String> update, TaskBatch.Task callback) {
        var counter = 1;
        var a0 = update.size();
        for (var s : update.keySet()) {
            var cache = resolveCachePath(update.get(s));
            var target = resolveRuntimePath(s);

            try {
                var title = "正在更新文件 第(%s/%s个)".formatted(counter, a0);
                callback.reportProgress(title, counter, a0);
                Files.createDirectories(target.getParent());
                Files.copy(cache, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update file: " + target, e);
            }
            counter++;
        }
    }

    private static Path resolveRuntimePath(String path) {
        return resolveInside(FilePath.RUNTIME.file().toPath(), path);
    }

    private static Path resolveCachePath(String resourceKey) {
        if (resourceKey == null || !resourceKey.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Invalid resource SHA-1: " + resourceKey);
        }
        return resolveInside(ClientFilePath.CACHE.file().toPath(), resourceKey);
    }

    private static Path resolveInside(Path base, String path) {
        var root = base.toAbsolutePath().normalize();
        // Protocol paths may have one leading separator, relative to the runtime root.
        var relative = path.startsWith("/") || path.startsWith("\\") ? path.substring(1) : path;
        var target = root.resolve(relative).normalize();
        if (target.equals(root) || !target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes or replaces root: " + path);
        }
        try {
            // Also reject links/junctions that would redirect operations outside the root.
            var existingRoot = root;
            while (!Files.exists(existingRoot, LinkOption.NOFOLLOW_LINKS)) {
                existingRoot = existingRoot.getParent();
            }
            var realRoot = existingRoot.toRealPath().resolve(existingRoot.relativize(root)).normalize();
            var existing = target;
            while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            var realTarget = existing.toRealPath().resolve(existing.relativize(target)).normalize();
            if (!realTarget.startsWith(realRoot)) {
                throw new IllegalArgumentException("Path escapes root through a link: " + path);
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot resolve operation path: " + path, error);
        }
        return target;
    }


    //======================================================
    // Field Access
    //======================================================
    public P02_ServerHandshake getServerInfo() {
        return serverInfo;
    }

    public ClientConnector getConnector() {
        return connector;
    }

    public ClientVersionRecord getLocalVersionInfo() {
        return this.info;
    }

    public ClientConfig getConfig() {
        return config;
    }
}
