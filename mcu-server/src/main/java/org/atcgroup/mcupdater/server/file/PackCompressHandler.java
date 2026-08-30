package org.atcgroup.mcupdater.server.file;

import me.gb2022.commons.math.SHA;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.util.FilePath;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PackCompressHandler implements FileAddHandler {
    public static final Logger LOGGER = LogManager.getLogger("FileAnalyzer/Fallback");
    private final Map<String, File> files = new HashMap<>();
    private final long compressionThreshold;

    public PackCompressHandler(ConfigurationSection config) {
        this.compressionThreshold = config.getLong("compression-threshold");
    }

    @Override
    public void onStart(VersionInfo info) {
        this.files.clear();
    }

    @Override
    public boolean handleNewFile(String relPath, File file, byte[] sha1, VersionInfo info) {
        if (file.length() <= this.compressionThreshold) {
            this.files.put(relPath, file);
            LOGGER.info("Compressing file: {}", relPath);
        } else {
            var source = Path.of(file.getAbsolutePath());
            var name = SHA.byteArrayToHexString(sha1);
            var target = Path.of(FilePath.runtime() + "/packs/" + name);

            info.addUpdateFile(relPath, name);

            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

    @Override
    public void onScanComplete(VersionInfo info) {
        if (this.files.isEmpty()) {
            LOGGER.info("No files found, compression discarded.");
        }

        var file = FilePath.resourcePack(info.getChannel(), UUID.randomUUID().toString());
        PatchFile.zip(new File(file), this.files);
    }
}
