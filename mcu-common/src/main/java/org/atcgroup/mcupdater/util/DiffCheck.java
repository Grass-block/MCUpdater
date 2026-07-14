package org.atcgroup.mcupdater.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public interface DiffCheck {
    static byte[] calculateSHA256(String filePath) throws IOException, NoSuchAlgorithmException {
        return calculateSHA256(new File(filePath));
    }

    static byte[] calculateSHA256(File file) {
        try (var fis = new FileInputStream(file); var channel = fis.getChannel()) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = ByteBuffer.allocate(8192); // 8 KB buffer
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
            return digest.digest();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static boolean compare(byte[] a1, byte[] a2) {
        for (int i = 0; i < 32; i++) {
            if (a1[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }
}
