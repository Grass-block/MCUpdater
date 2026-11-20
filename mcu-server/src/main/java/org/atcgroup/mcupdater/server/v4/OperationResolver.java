package org.atcgroup.mcupdater.server.v4;

import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.server.v4.data.VersionInfo;
import org.atcgroup.mcupdater.server.v4.storage.CASResourceStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Applies delete, extract, and replace operations from a version manifest. */
public final class OperationResolver {
    private final Path root;
    private final CASResourceStorage resources;

    public OperationResolver(Path root, CASResourceStorage resources) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public void apply(VersionInfo version) throws IOException {
        Objects.requireNonNull(version, "version");
        for (var path : version.getDelete()) Files.deleteIfExists(resolveTarget(path));

        for (var sha1 : version.getExtract()) {
            var pack = this.resources.getResource(sha1)
                    .orElseThrow(() -> new IOException("Missing extract resource: " + sha1));
            var base = this.root.toString() + java.io.File.separator;
            PatchFile.unzip(pack, base);
        }

        for (var entry : version.getUpdate().entrySet()) {
            var source = this.resources.getResource(entry.getValue())
                    .orElseThrow(() -> new IOException("Missing update resource: " + entry.getValue()));
            var target = resolveTarget(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveTarget(String path) {
        Objects.requireNonNull(path, "path");
        var normalized = path.startsWith("/") || path.startsWith("\\") ? path.substring(1) : path;
        var target = this.root.resolve(normalized).normalize();
        if (!target.startsWith(this.root)) throw new IllegalArgumentException("Path escapes version root: " + path);
        return target;
    }
}
