package org.atcgroup.mcupdater.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public interface FileDigest {
    private static byte[] calculate(File file, MessageDigest digest) throws IOException {
        try (var fis = new FileInputStream(file); var channel = fis.getChannel()) {
            var buffer = ByteBuffer.allocate(8192); // 8 KB buffer
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
            return digest.digest();
        }
    }

    static byte[] calculateSHA512(File file) {
        try {
            return calculate(file, MessageDigest.getInstance("SHA-512"));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] calculateSHA384(File file) {
        try {
            return calculate(file, MessageDigest.getInstance("SHA-384"));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] calculateSHA256(File file) {
        try {
            return calculate(file, MessageDigest.getInstance("SHA-256"));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] calculateSHA1(File file) {
        try {
            return calculate(file, MessageDigest.getInstance("SHA-1"));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean compare(byte[] a1, byte[] a2) {
        if (a1.length != a2.length) {
            return false;
        }

        for (int i = 0; i < 32; i++) {
            if (a1[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }
}
