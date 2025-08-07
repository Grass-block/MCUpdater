package org.atcraftmc.updater.server.file;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public interface DiffCheck {
    static byte[] calculateSHA256(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(filePath); FileChannel channel = fis.getChannel(); DigestInputStream dis = new DigestInputStream(
                fis,
                digest
        )) {
            ByteBuffer buffer = ByteBuffer.allocate(8192); // 8 KB buffer
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
            return digest.digest();
        }
    }
}
