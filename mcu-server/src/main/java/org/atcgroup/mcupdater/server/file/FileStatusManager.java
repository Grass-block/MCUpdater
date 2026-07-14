package org.atcgroup.mcupdater.server.file;

import me.gb2022.commons.math.SHA;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.util.DiffCheck;
import org.atcraftmc.updater.util.FilePath;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class FileStatusManager {
    public static final Logger LOGGER = LogManager.getLogger("Server");

    private final String id;
    private final String path;
    private final Set<String> rejectList = new HashSet<>();
    private final Set<String> acceptList = new HashSet<>();
    private final Set<String> forcedRejectList = new HashSet<>();

    public FileStatusManager(String id, ConfigurationSection config) {
        this.id = id;
        this.path = config.getString("path");
        this.rejectList.addAll(config.getStringList("filter-reject"));
        this.acceptList.addAll(config.getStringList("filter-add"));
        if (config.contains("filter-block")) {
            this.forcedRejectList.addAll(config.getStringList("filter-block"));
        }
    }

    static byte[] calculateSHA256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file.getAbsolutePath()); FileChannel channel = fis.getChannel()) {
                ByteBuffer buffer = ByteBuffer.allocate(8192); // 8 KB buffer
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
                return digest.digest();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void iterate(File folder, Consumer<File> accepter) {
        var f = folder.listFiles();

        if (f == null) {
            return;
        }

        for (var ff : Objects.requireNonNull(f)) {
            if (ff.isDirectory()) {
                iterate(ff, accepter);
                continue;
            }

            accepter.accept(ff);
        }
    }

    public String shaFile(String path) {
        var sha = SHA.getSHA256(path, false);
        return "%s/diff/%s/%s/%s.sha256".formatted(FilePath.runtime(), this.id, sha.substring(0, 2), sha);
    }

    public String fixPath(File file) {
        return file.getAbsolutePath().replace(File.separatorChar, '/').substring(this.path.length());
    }

    public FileModifyStatus checkFileStatus(String path) {
        var sha = new File(shaFile(path));
        var file = new File(this.path + path);

        var hash = calculateSHA256(file);

        if (!sha.exists() || sha.length() == 0) {
            sha.getParentFile().mkdirs();
            //LOGGER.info("created directory {}", sha.getParentFile().getAbsolutePath());

            try (var o = new FileOutputStream(sha)) {
                o.write(hash);
                o.write(path.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return FileModifyStatus.ADD;
        }

        try (var in = new FileInputStream(sha)) {
            var bytes = new byte[32];

            //im pretty sure checksum is always longer than 32 otherwise its fucking corrupted
            if (in.read(bytes) < 32) {
                //but this will never happen since the file is needed to be longer than 32...
                LOGGER.error("find incorrect sha file {}", sha.getAbsolutePath());
            }

            //only case if checksum is match!
            if (DiffCheck.compare(bytes, hash)) {
                return FileModifyStatus.NONE;
            }

            try (var o = new FileOutputStream(sha)) {
                o.write(hash);
                o.write(in.readAllBytes());
            }
            return FileModifyStatus.UPDATE;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Set<String> recordedFiles() {
        var folder = new File("%s/diff/%s/".formatted(FilePath.runtime(), this.id));
        var list = new HashSet<String>();


        iterate(folder, (f) -> {
            try (var in = new FileInputStream(f)) {
                var bytes = new byte[32];

                if (in.read(bytes) < 32) {
                    throw new RuntimeException("What the fuck?");
                }

                list.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return list;
    }

    public void iterate(BiConsumer<String, File> accepter) {
        var folder = new File(this.path);
        iterate(folder, (f) -> {
            var path = fixPath(f);

            if (this.forcedRejectList.stream().anyMatch(path::startsWith)) {
                return;
            }

            if (this.acceptList.stream().noneMatch(path::startsWith)) {
                if (this.rejectList.stream().anyMatch(path::startsWith)) {
                    return;
                }
            }

            accepter.accept(path, f);
        });
    }

    public Map<String, FileModifyStatus> check() {
        var result = new HashMap<String, FileModifyStatus>();
        iterate((p, f) -> result.put(p, checkFileStatus(p)));

        for (var s : recordedFiles()) {
            if (result.containsKey(s)) {
                continue;
            }

            result.put(s, FileModifyStatus.DELETE);
            new File(shaFile(s)).delete();
        }

        return result;
    }
}
