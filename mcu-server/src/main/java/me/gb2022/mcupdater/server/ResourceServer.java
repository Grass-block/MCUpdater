package me.gb2022.mcupdater.server;

import me.gb2022.mcupdater.rtp.RTPServer;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.ConfigurationSection;

public class ResourceServer {
    public static final Logger LOGGER = LogManager.getLogger("ResourceServer");

    private final CASResourceStorage storage;
    private final RTPServer server;

    public ResourceServer(CASResourceStorage storage) {
        this.storage = storage;
        this.server = new RTPServer(storage);
    }

    public void init(ConfigurationSection config, NettyChannelInitializer initializer) {
        this.server.setMaxWorkers(config.getInt("bftp-max-worker"));
        if(config.getBoolean("http-enable")){
            var host = config.getString("http-endpoint");
            var port = config.getInt("http-port");

            try {
                this.server.startHttp(port,host);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            LOGGER.info("HTTP Server started on port {}, using endpoint '{}'",port,host);
        }

        initializer.handler(this.server::masterHandler);
    }

    public void dispose() {
        this.server.close();
    }

    public RTPServer getServer() {
        return server;
    }

    public CASResourceStorage getStorage() {
        return storage;
    }
}
