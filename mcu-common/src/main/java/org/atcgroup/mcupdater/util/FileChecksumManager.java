package org.atcgroup.mcupdater.util;

import me.gb2022.commons.file.FilePath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FileChecksumManager {
    private final FilePath root;
    private final Map<String, byte[]> checksums = new HashMap<>();

    public FileChecksumManager(FilePath root) {
        this.root = root;
    }

    private String key(String owner, String name) {
        return owner + ":" + name;
    }

    private File getChecksumFile(String owner, String path) {
        return this.root.append(owner).append(path + ".sum").file();
    }

    public byte[] updateFileChecksum(String owner, String path) {
        var std = getChecksumFile(owner, path);
        var file = this.root.append(owner).append(path).file();
        var k = key(owner, path);

        if (!file.exists() || file.length() == 0) {
            return null;
        }

        if (!std.exists() || std.length() == 0) {
            std.getParentFile().mkdirs();
            try {
                std.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var sum = DiffCheck.calculateSHA256(file);

        this.checksums.put(k, sum);

        try (var o = new FileOutputStream(std)) {
            o.write(sum);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return sum;
    }

    public byte[] getFileChecksum(String owner, String path) {
        var k = this.key(owner, path);

        if (this.checksums.containsKey(k)) {
            return this.checksums.get(k);
        }

        var std = getChecksumFile(owner, path);

        if (!std.exists() || std.length() == 0) {
            return updateFileChecksum(owner, path);
        }

        try (var in = new FileInputStream(std)) {
            var sum = in.readAllBytes();

            this.checksums.put(k, sum);
            return sum;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
