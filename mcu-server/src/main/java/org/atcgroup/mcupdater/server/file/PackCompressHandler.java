package org.atcgroup.mcupdater.server.file;

import me.gb2022.commons.math.SHA;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.util.FilePaths;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PackCompressHandler implements FileAddHandler {
    public static final Logger LOGGER = LogManager.getLogger("FileAnalyzer/Fallback");
    private final Map<String, File> files = new HashMap<>();
    private final long compressionThreshold;

    public PackCompressHandler(ConfigurationSection config) {
        this.compressionThreshold = config.getInt("compress-threshold");
    }

    @Override
    public void handleFiles(Set<SourceFileInfo> files, VersionInfo info) {
        this.files.clear();

        FileAddHandler.super.handleFiles(files, info);

        if (this.files.isEmpty()) {
            LOGGER.info("No files found, compression discarded.");
        }

        var file = FilePaths.resourcePack(info.getChannel(), UUID.randomUUID().toString());
        PatchFile.zip(new File(file), this.files);
    }

    @Override
    public boolean handleNewFile(String relPath, File file, byte[] sha1, VersionInfo info) {
        if (file.length() <= this.compressionThreshold) {
            this.files.put(relPath, file);
            LOGGER.info("Compressing file: {}", relPath);
        } else {
            var source = file.toPath();
            var name = SHA.byteArrayToHexString(sha1);
            var target = Path.of(FilePaths.runtime() + "/packs/" + name);

            info.addUpdateFile(relPath, name);

            if(target.toFile().exists()) {
                LOGGER.info("Skipping file: {}", SHA.byteArrayToHexString(sha1));
                return true;
            }

            try {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }
}
