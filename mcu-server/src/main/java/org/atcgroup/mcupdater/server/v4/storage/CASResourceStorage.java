package org.atcgroup.mcupdater.server.v4.storage;

import me.gb2022.commons.file.FilePath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.util.FileDigest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CASResourceStorage implements AutoCloseable {
    public static final Logger LOGGER = LogManager.getLogger("ResourceStorage");

    private final Map<String, Object> objectLocks = new ConcurrentHashMap<>();
    private final CASMetadataStorage metadata;
    private final FilePath root;

    public CASResourceStorage(FilePath root) {
        this.root = root;
        this.metadata = new CASMetadataStorage(root.file().toPath());
    }

    private File object(String sha) {
        return this.root.append(sha.substring(0, 2)).append(sha).file();
    }

    public void registerResource(File file) {
        var sha1 = HexFormat.of().formatHex(FileDigest.calculateSHA1(file));
        var destination = object(sha1);
        synchronized (resourceMonitor(destination)) {
            this.metadata.ensureOpen();
            if (this.metadata.get(sha1).isPresent() && destination.isFile()) {
                return;
            }

            try {
                Files.createDirectories(Path.of(destination.getParent()));
                Files.copy(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            var storedSha1 = HexFormat.of().formatHex(FileDigest.calculateSHA1(destination));
            if (!sha1.equals(storedSha1)) {
                throw new IllegalStateException("Resource changed while importing: " + file);
            }

            var sha512 = HexFormat.of().formatHex(FileDigest.calculateSHA512(destination));
            try (var output = java.nio.channels.FileChannel.open(destination.toPath(), java.nio.file.StandardOpenOption.WRITE)) {
                output.force(true);
            } catch (IOException error) {
                throw new java.io.UncheckedIOException("Failed to persist CAS resource", error);
            }
            this.metadata.put(new CASObjectMeta(destination.length(), sha1, sha512));
        }
        LOGGER.info("Resource Registered: {}(src:{})", sha1, file.getName());
    }

    public Optional<File> getResource(String sha1) {
        return getResourceInfo(sha1).map(info -> object(info.getSha1())).filter(File::isFile);
    }

    public Optional<CASObjectMeta> getResourceInfo(String sha1) {
        CASMetadataStorage.validateSha1(sha1);
        return this.metadata.get(sha1);
    }

    /**
     * Stop users of this shared storage before closing it; FTPServer does not own the storage.
     */
    @Override
    public void close() {
        this.metadata.close();
    }

    public FilePath getRoot() {
        return root;
    }

    private Object resourceMonitor(File file) {
        return this.objectLocks.computeIfAbsent(file.getAbsolutePath(), key -> new Object());
    }
}
