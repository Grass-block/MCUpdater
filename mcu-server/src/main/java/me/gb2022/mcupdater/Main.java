package me.gb2022.mcupdater;

import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.cdn.MCUpdaterCDNServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.FileOutputStream;
import java.io.IOException;

public interface Main {
    Logger LOGGER = LogManager.getLogger("BOOT");

    static void main(String[] args) {
        if (args.length != 1) {
            nagBootOpts();
            return;
        }

        if (args[0].equals("--cdn")) {
            new MCUpdaterCDNServer().run();
            return;
        }
        if (args[0].equals("--server")) {
            return;
        }

        nagBootOpts();
    }

    static void nagBootOpts() {
        LOGGER.error("=======================[ERROR]=======================");
        LOGGER.error("Please specify boot option in program param:");
        LOGGER.error("  1) '--server' to run as server");
        LOGGER.error("  2) '--cdn' to run as cdn server");
        LOGGER.error("  3) remain no param to let server control.");
        LOGGER.error("=====================================================");
    }

    static boolean loadConfig(String name,YamlConfiguration config) {
        var configFile = FilePath.RUNTIME.append(name).file();

        if (!configFile.exists() || configFile.length() == 0) {
            LOGGER.error("Configuration invalid and re-generated.");
            LOGGER.error("Please configured it before restarting.");

            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
                try (var o = new FileOutputStream(configFile); var in = Main.class.getResourceAsStream("/" + name)) {
                    assert in != null;
                    in.transferTo(o);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return false;
        }

        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.error("Invalid configuration found :(");
            LOGGER.catching(e);
            return false;
        }

        return true;
    }
}
