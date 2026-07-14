package org.atcgroup.mcupdater.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.data.ServerMeta;
import org.atcgroup.mcupdater.server.file.FileService;
import org.atcraftmc.updater.util.FilePath;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public final class MCUpdaterServer {
    public static final Logger LOGGER = LogManager.getLogger("Server");
    public static final MCUpdaterServer INSTANCE = new MCUpdaterServer();
    private final FileConfiguration config = new YamlConfiguration();
    private final ConsoleService consoleService = new ConsoleService();
    private final NetworkService networkService = new NetworkService();
    private final FileService fileService = new FileService();
    private final VersionService versionService = new VersionService(this.fileService);

    public static MCUpdaterServer instance() {
        return INSTANCE;
    }

    public boolean loadConfiguration() {
        var file = new File(FilePath.runtime() + "/config.yml");

        if (!file.exists() || file.length() == 0) {
            LOGGER.warn("没有找到默认的配置文件，正在覆盖生成...");

            try (var out = new FileOutputStream(file); var in = this.getClass().getResourceAsStream("/config.yml")) {
                out.write(Objects.requireNonNull(in).readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            LOGGER.info("配置文件生成于 {}", file.getAbsolutePath());

            return false;
        }

        try {
            this.config.load(file);
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.warn("读取配置文件时发生错误!");
            LOGGER.catching(e);

            return false;
        }
    }

    public void run() {
        this.consoleService.init();

        if (!this.loadConfiguration()) {
            return;
        }

        this.consoleService.start();
        this.fileService.start();
        this.versionService.start();
        this.networkService.start();
    }

    public ConfigurationSection config() {
        return this.config.getConfigurationSection("config");
    }

    public ServerMeta createSession() {
        var uuid = UUID.randomUUID().toString();
        var cdn = this.config().getBoolean("cdn-server.enable");
        var address = this.config().getString("cdn-server.address");
        var port = this.config().getInt("cdn-server.address");

        return new ServerMeta("mcu-server_prod", "4.0.0", uuid, cdn, address, port);
    }

    public void stop() {
        this.consoleService.stop();
        this.networkService.stop();
    }

    public ConsoleService getConsoleService() {
        return consoleService;
    }

    public FileService getFileService() {
        return fileService;
    }

    public NetworkService getNetworkService() {
        return networkService;
    }

    public VersionService getVersionService() {
        return versionService;
    }
}
