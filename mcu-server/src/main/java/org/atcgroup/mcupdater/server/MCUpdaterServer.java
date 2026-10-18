package org.atcgroup.mcupdater.server;

import me.gb2022.commons.file.FilePath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.data.ServerMeta;
import org.atcgroup.mcupdater.server.service.ServiceManager;
import org.atcgroup.mcupdater.util.I18n;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MCUpdaterServer {
    public static final Logger LOGGER = LogManager.getLogger("Server");
    public static final MCUpdaterServer INSTANCE = new MCUpdaterServer();

    private final FileRepository packsFolder = new FileRepository(FilePath.RUNTIME.append("packs"));
    private final ServiceManager serviceManager = new ServiceManager(this);
    private final FileConfiguration config = new YamlConfiguration();
    private final ConsoleService consoleService = new ConsoleService();

    private final ExecutorService executorService = Executors.newCachedThreadPool();


    public FileRepository getPacksFolder() {
        return packsFolder;
    }

    public static MCUpdaterServer instance() {
        return INSTANCE;
    }

    public boolean loadConfiguration() {
        var file = FilePath.RUNTIME.append("config.yml").file();

        if (!file.exists() || file.length() == 0) {
            LOGGER.warn(I18n.message("server.config.not_found"));

            try (var out = new FileOutputStream(file); var in = this.getClass().getResourceAsStream("/config.yml")) {
                out.write(Objects.requireNonNull(in).readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            LOGGER.info(I18n.message("server.config.generated", file.getAbsolutePath()));

            return false;
        }

        try {
            this.config.load(file);
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.warn(I18n.message("server.config.load_error"));
            LOGGER.catching(e);

            return false;
        }
    }

    public void run() {
        var last = System.currentTimeMillis();

        this.consoleService.init();

        if (!this.loadConfiguration()) {
            return;
        }

        this.serviceManager.fireBootstrap(this,this.config());
        this.consoleService.start();

        var passed = System.currentTimeMillis() - last;

        LOGGER.info(I18n.message("bootstrap.complete", passed));
    }

    public ConfigurationSection config() {
        return this.config.getConfigurationSection("config");
    }

    public ServerMeta createSession() {
        var uuid = UUID.randomUUID().toString();
        var cdn = this.config().getBoolean("cdn-service.mcu-cdn.enable");
        var address = this.config().getString("cdn-service.mcu-cdn.address");
        var port = this.config().getInt("cdn-service.mcu-cdn.port");
        var repo = this.config().getString("cdn-service.mcu-cdn.repository");

        return new ServerMeta("mcu-server_prod", "4.0.0", uuid, cdn, address, port, repo);
    }

    public void stop() {
        this.consoleService.stop();
        this.serviceManager.fireServerClose(this);
    }

    public ConsoleService getConsoleService() {
        return consoleService;
    }

    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    public ExecutorService getExecutor() {
        return executorService;
    }
}
