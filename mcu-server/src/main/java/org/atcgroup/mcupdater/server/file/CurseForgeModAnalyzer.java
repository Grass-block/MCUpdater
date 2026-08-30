package org.atcgroup.mcupdater.server.file;

import org.atcgroup.mcupdater.data.VersionInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

//todo: CF API需要个人APIKey，暂时不再做支持。
//SHIT!
public final class CurseForgeModAnalyzer implements FileAddHandler {
    private static final int CF_FP_MAGIC = 0x5BD1E995; // 1540483477 WTF?


    public static long calculate(Path file) throws IOException {
        int normalizedLength = 0;

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];

            int read;
            while ((read = in.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (!isWhitespace(buffer[i] & 0xFF)) {
                        normalizedLength++;
                    }
                }
            }
        }

        int hash = 1 ^ normalizedLength;

        int packed = 0;
        int packedBits = 0;

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];

            int read;
            while ((read = in.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    int b = buffer[i] & 0xFF;

                    if (isWhitespace(b)) {
                        continue;
                    }

                    packed |= b << packedBits;
                    packedBits += 8;

                    if (packedBits == 32) {
                        int num6 = packed * CF_FP_MAGIC;
                        int num7 = (num6 ^ (num6 >>> 24)) * CF_FP_MAGIC;

                        hash = hash * CF_FP_MAGIC ^ num7;

                        packed = 0;
                        packedBits = 0;
                    }
                }
            }
        }

        if (packedBits > 0) {
            hash = (hash ^ packed) * CF_FP_MAGIC;
        }

        int num6 = (hash ^ (hash >>> 13)) * CF_FP_MAGIC;

        return Integer.toUnsignedLong(num6 ^ (num6 >>> 15));
    }

    private static boolean isWhitespace(int b) {
        return b == 9      // \t
                || b == 10 // \n
                || b == 13 // \r
                || b == 32; // space
    }

    @Override
    public boolean handleNewFile(String relPath, File file, byte[] sha1, VersionInfo info) {
        return false;
    }
}
