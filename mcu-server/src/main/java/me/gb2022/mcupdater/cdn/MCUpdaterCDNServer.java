package me.gb2022.mcupdater.cdn;

import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.Main;
import me.gb2022.mcupdater.server.ResourceServer;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;

public final class MCUpdaterCDNServer {
    public static final Logger LOGGER = LogManager.getLogger("CDN/Main");
    private final CASResourceStorage storage = new CASResourceStorage(FilePath.RUNTIME.append("storage"));
    private final ResourceServer resourceServer = new ResourceServer(this.storage);
    private final YamlConfiguration config = new YamlConfiguration();
    private final CDNNetworkServer networkServer = new CDNNetworkServer();

    public void run() {
        if (!Main.loadConfig("cdn-config.yml", this.config)) {
            LOGGER.error("Unable to load cdn-config.yml.");
            return;
        }

        var port = this.config.getInt("config.server-port");
        var records = new HashMap<String, String>();
        var s = this.config.getConfigurationSection("config.cdn-access");

        for (var k : s.getKeys(false)) {
            records.put(k, s.getString(k));
            LOGGER.info("Adding server registration: '{}' -> '{}'", k, records.get(k));
        }

        try {
            this.networkServer.run(port, (i) -> {
                i.handler(() -> new CDNTransferHandler(records, this.storage));
                this.resourceServer.init(this.config.getConfigurationSection("config.resource-server"), i);
            });
            LOGGER.info("CDN server started on port {}.", port);
        } catch (Exception e) {
            LOGGER.error("An error occurred: ");
            LOGGER.catching(e);
        } finally {
            this.networkServer.close();
            this.storage.close();
        }
    }
}
