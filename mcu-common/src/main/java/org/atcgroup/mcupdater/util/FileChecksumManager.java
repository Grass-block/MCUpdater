package org.atcgroup.mcupdater.util;

import me.gb2022.commons.file.FilePath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class FileChecksumManager {
    private final FilePath root;
    private final Map<String, byte[]> checksums = new HashMap<>();

    public FileChecksumManager(FilePath root) {
        this.root = root;
    }

    private File getChecksumFile(String path) {
        return this.root.append(path + ".sum").file();
    }

    public byte[] updateFileChecksum(String path) {
        var std = getChecksumFile(path);
        var file = this.root.append(path).file();

        if (!file.exists() || file.length() == 0) {
            return new byte[0];
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

        this.checksums.put(path, sum);

        try (var o = new FileOutputStream(std)) {
            o.write(sum);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return sum;
    }

    public byte[] getFileChecksum(String path) {

        if (this.checksums.containsKey(path) && this.checksums.get(path) != null) {
            return this.checksums.get(path);
        }

        var std = getChecksumFile(path);

        if (!std.exists() || std.length() == 0) {
            return updateFileChecksum(path);
        }

        try (var in = new FileInputStream(std)) {
            var sum = in.readAllBytes();

            this.checksums.put(path, sum);
            return sum;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FilePath getRoot() {
        return this.root;
    }
}
