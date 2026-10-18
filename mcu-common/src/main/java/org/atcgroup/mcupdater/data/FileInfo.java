package org.atcgroup.mcupdater.data;

import me.gb2022.commons.math.SHA;
import org.atcgroup.mcupdater.util.DiffCheck;

import java.io.File;
import java.util.HexFormat;

@SuppressWarnings("ClassCanBeRecord")
public final class FileInfo {
    private final String name;
    private final long length;
    private final String sha1;
    private final String sha256;

    public static FileInfo calculateOf(File file) {
        var sha256 = SHA.byteArrayToHexString(DiffCheck.calculateSHA256(file));
        var sha1 = SHA.byteArrayToHexString(DiffCheck.calculateSHA1(file));

        if(!file.exists()) {
            return null;
        }

        return new FileInfo(file.getName(), file.length(), sha1, sha256);
    }

    public FileInfo(String name, long length, String sha1, String sha256) {
        this.name = name;
        this.length = length;
        this.sha1 = sha1;
        this.sha256 = sha256;
    }

    public String getName() {
        return name;
    }

    public long getLength() {
        return length;
    }

    public String getSha1() {
        return sha1;
    }

    public String getSha256() {
        return sha256;
    }

    public byte[] getSha256Raw() {
        return HexFormat.of().parseHex(this.sha256);
    }

    public byte[] getSha1Raw() {
        return HexFormat.of().parseHex(this.sha1);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FileInfo info)) {
            return false;
        }

        if (!info.getName().equals(this.name)) {
            return false;
        }

        if (info.getLength() != this.length) {
            return false;
        }

        if (this.sha1 == null || sha256 == null) {
            return false;
        }

        return this.sha1.equals(info.getSha1());
    }
}
