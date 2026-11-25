package me.gb2022.mcupdater.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteOptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

/**
 * Persistent metadata only; resource contents remain in the CAS object directories.
 */
public final class CASMetadataStorage implements AutoCloseable {
    private static final byte[] LEGACY_MIGRATED = bytes("migration:json-v1");
    private final DB database;
    private boolean closed;

    public CASMetadataStorage(Path root) {
        try {
            Files.createDirectories(root);
            this.database = factory.open(root.resolve("_metadata").toFile(), new Options().createIfMissing(true)
                                                                                          .compressionType(CompressionType.NONE));
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to open CAS metadata", error);
        }
        try {
            migrate(root.resolve("_index.json"));
        } catch (RuntimeException error) {
            try {
                this.database.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    public synchronized Optional<CASObjectMeta> get(String sha1) {
        ensureOpen();
        var value = this.database.get(key(sha1));
        return value == null ? Optional.empty() : Optional.of(decode(sha1, JsonParser.parseString(new String(value, StandardCharsets.UTF_8))
                                                                                     .getAsJsonObject()));
    }

    public synchronized void put(CASObjectMeta info) {
        ensureOpen();
        validateSha1(info.getSha1());
        this.database.put(key(info.getSha1()), encode(info), new WriteOptions().sync(true));
    }

    /**
     * A single atomic batch makes interrupted legacy migration safe to retry.
     */
    private void migrate(Path legacy) {
        if (this.database.get(LEGACY_MIGRATED) != null) {
            return;
        }

        try (var batch = this.database.createWriteBatch()) {
            if (Files.exists(legacy) && Files.size(legacy) != 0) {
                var entries = JsonParser.parseString(Files.readString(legacy)).getAsJsonObject();
                for (var entry : entries.entrySet()) {
                    var sha1 = entry.getKey().toLowerCase(Locale.ROOT);
                    var info = decode(sha1, entry.getValue().getAsJsonObject());
                    if (this.database.get(key(sha1)) == null) {
                        batch.put(key(sha1), encode(info));
                    }
                }
            }
            batch.put(LEGACY_MIGRATED, new byte[]{1});
            this.database.write(batch, new WriteOptions().sync(true));
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to migrate CAS metadata", error);
        }
    }

    private static CASObjectMeta decode(String sha1, JsonObject json) {
        validateSha1(sha1);
        long length = json.get("length").getAsLong();
        var indexedSha1 = json.get("sha1").getAsString().toLowerCase(Locale.ROOT);
        if (length < 0 || !sha1.equals(indexedSha1)) {
            throw new IllegalArgumentException("Invalid CAS metadata: " + sha1);
        }
        return new CASObjectMeta(length, sha1, json.get("sha512")
                                                   .getAsString(), json.has("sourceLink") ? json.get("sourceLink")
                                                                                                .getAsString() : CASObjectMeta.NO_DOWNLOAD_LINK);
    }

    private static byte[] encode(CASObjectMeta info) {
        var json = new JsonObject();
        json.addProperty("length", info.getLength());
        json.addProperty("sha1", info.getSha1());
        json.addProperty("sha512", info.getSha512());
        json.addProperty("sourceLink", info.getSourceLink().orElse(CASObjectMeta.NO_DOWNLOAD_LINK));
        return bytes(json.toString());
    }

    static void validateSha1(String sha1) {
        if (sha1 == null || !sha1.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-1 index");
        }
    }

    private static byte[] key(String sha1) {
        return bytes("resource:" + sha1);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    synchronized void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("CAS metadata storage is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        try {
            this.database.close();
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to close CAS metadata", error);
        }
    }
}
