package org.atcgroup.mcupdater.server.v4;

import me.gb2022.commons.file.FilePath;
import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.server.v4.data.VersionInfo;
import org.atcgroup.mcupdater.server.v4.storage.CASResourceStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VersionOperationTest {
    @TempDir
    Path directory;

    @Test
    void parsesManifestAndAppliesDeleteExtractAndUpdate() throws Exception {
        var json = """
                {
                  "channel":"server",
                  "version":"initial",
                  "timestamp":1790660497903,
                  "assets":{"server":["%s"],"download":{"%s":{"url":"https://example.com/file/test","length":350234}}},
                  "extract":["%s"],
                  "update":{"/config/test1.cfg":"%s"},
                  "delete":["/config/delete.cfg"]
                }
                """;
        var old = Files.writeString(this.directory.resolve("old"), "old content");
        var replacement = Files.writeString(this.directory.resolve("replacement"), "new content");
        var packedSource = Files.writeString(this.directory.resolve("packed"), "from patch pack");
        var pack = this.directory.resolve("pack.zip");
        PatchFile.zip(pack.toFile(), Map.of("config/extracted.cfg", packedSource.toFile()));
        try (var resources = new CASResourceStorage(new FilePath(this.directory.resolve("cas").toString()))) {
            resources.registerResource(replacement.toFile());
            resources.registerResource(pack.toFile());
            var replacementId = hash(replacement);
            var packId = hash(pack);
            var manifest = VersionInfo.fromJson(json.formatted("a".repeat(40), "b".repeat(40), packId, replacementId));
            assertEquals("server", manifest.getChannel());
            assertEquals(1790660497903L, manifest.getTimestamp());
            assertEquals(350234L, manifest.getAssets().getDownload().get("b".repeat(40)).getLength());
            assertEquals(manifest.getUpdate(), VersionInfo.fromJson(manifest.toJson()).getUpdate());

            var root = this.directory.resolve("runtime");
            Files.createDirectories(root.resolve("config"));
            Files.writeString(root.resolve("config/delete.cfg"), "remove me");
            Files.writeString(root.resolve("config/test1.cfg"), "old version");
            new OperationResolver(root, resources).apply(manifest);

            assertFalse(Files.exists(root.resolve("config/delete.cfg")));
            assertEquals("new content", Files.readString(root.resolve("config/test1.cfg")));
            assertEquals("from patch pack", Files.readString(root.resolve("config/extracted.cfg")));
        }
    }

    @Test
    void rejectsManifestPathsOutsideRuntimeRoot() throws Exception {
        var version = VersionInfo.fromJson("""
                {"channel":"server","version":"initial","timestamp":1,
                 "assets":{"server":[],"download":{}},"extract":[],"update":{},"delete":["/../../outside"]}
                """);
        try (var resources = new CASResourceStorage(new FilePath(this.directory.resolve("cas").toString()))) {
            assertThrows(IllegalArgumentException.class, () -> new OperationResolver(this.directory.resolve("runtime"), resources)
                    .apply(version));
        }
    }

    private static String hash(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    }
}
