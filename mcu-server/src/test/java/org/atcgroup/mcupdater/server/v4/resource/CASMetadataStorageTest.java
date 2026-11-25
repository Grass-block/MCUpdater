package org.atcgroup.mcupdater.server.v4.resource;

import com.google.gson.JsonObject;
import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.storage.CASMetadataStorage;
import me.gb2022.mcupdater.storage.CASObjectMeta;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import org.atcgroup.mcupdater.util.FileDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class CASMetadataStorageTest {
    @TempDir
    Path directory;

    @Test
    void registrationPersistsWithoutExplicitSaveAndDeduplicates() throws Exception {
        Path source = Files.write(this.directory.resolve("source"), new byte[]{1, 2, 3});
        String sha1 = HexFormat.of().formatHex(FileDigest.calculateSHA1(source.toFile()));
        var root = new FilePath(this.directory.resolve("cas").toString());
        Path object;
        try (var storage = new CASResourceStorage(root)) {
            storage.registerResource(source.toFile());
            object = storage.getResource(sha1).orElseThrow().toPath();
            var modified = Files.getLastModifiedTime(object);
            storage.registerResource(source.toFile());
            assertEquals(modified, Files.getLastModifiedTime(object));
            assertFalse(Files.exists(root.file().toPath().resolve("_index.json")));
        }
        try (var storage = new CASResourceStorage(root)) {
            assertEquals(object, storage.getResource(sha1).orElseThrow().toPath());
            var info = storage.getResourceInfo(sha1).orElseThrow();
            assertEquals(3, info.getLength());
            assertEquals(sha1, info.getSha1());
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(object));
            assertThrows(IllegalArgumentException.class, () -> storage.getResource(sha1.toUpperCase(java.util.Locale.ROOT)));
        }
    }

    @Test
    void retainsLongLengthsAndSourceLinksAfterReopen() {
        String sha1 = "a".repeat(40);
        long length = 1L << 34;
        try (var metadata = new CASMetadataStorage(this.directory)) {
            metadata.put(new CASObjectMeta(length, sha1, "b".repeat(128), "https://example.com/file"));
        }
        try (var metadata = new CASMetadataStorage(this.directory)) {
            var info = metadata.get(sha1).orElseThrow();
            assertEquals(length, info.getLength());
            assertEquals("b".repeat(128), info.getSha512());
            assertEquals("https://example.com/file", info.getSourceLink().orElseThrow());
            assertTrue(metadata.get("c".repeat(40)).isEmpty());
        }
    }

    @Test
    void migratesLegacyJsonOnceAndPreservesIt() throws Exception {
        String sha1 = "a".repeat(40);
        var legacy = new JsonObject();
        var entry = new JsonObject();
        entry.addProperty("length", 1L << 34);
        entry.addProperty("sha1", sha1.toUpperCase(java.util.Locale.ROOT));
        entry.addProperty("sha512", "b".repeat(128));
        legacy.add(sha1.toUpperCase(java.util.Locale.ROOT), entry);
        Path index = Files.writeString(this.directory.resolve("_index.json"), legacy.toString());
        try (var metadata = new CASMetadataStorage(this.directory)) {
            assertEquals(1L << 34, metadata.get(sha1).orElseThrow().getLength());
            assertTrue(metadata.get(sha1).orElseThrow().getSourceLink().isEmpty());
            metadata.put(new CASObjectMeta(42, sha1, "c".repeat(128)));
        }
        assertEquals(legacy.toString(), Files.readString(index));
        try (var metadata = new CASMetadataStorage(this.directory)) {
            assertEquals(42, metadata.get(sha1).orElseThrow().getLength());
        }
    }

    @Test
    void failedMigrationReleasesDatabaseAndCanBeRetried() throws Exception {
        Path index = Files.writeString(this.directory.resolve("_index.json"), "invalid json");
        assertThrows(RuntimeException.class, () -> new CASMetadataStorage(this.directory));
        Files.writeString(index, "{}");
        try (var metadata = new CASMetadataStorage(this.directory)) {
            assertTrue(metadata.get("a".repeat(40)).isEmpty());
        }
    }

    @Test
    void closeIsIdempotentAndRejectsFurtherAccess() {
        var metadata = new CASMetadataStorage(this.directory);
        metadata.close();
        metadata.close();
        assertThrows(IllegalStateException.class, () -> metadata.get("a".repeat(40)));
        assertThrows(IllegalStateException.class, () -> metadata.put(new CASObjectMeta(0, "a".repeat(40), "")));
        try (var reopened = new CASMetadataStorage(this.directory)) {
            assertTrue(reopened.get("a".repeat(40)).isEmpty());
        }
    }
}
