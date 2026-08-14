package org.atcgroup.mcupdater.server.service;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import me.gb2022.simpnet.packet.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.util.I18n;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public final class ServiceManager {
    private static final Logger LOGGER = LogManager.getLogger("ServiceManager");
    public Map<Class<? extends Service>, Service> services = new HashMap<>(16);

    public ServiceManager() {
        registerService(new ResourcePackService());
        registerService(new VersionService());
        registerService(new NetworkService());
        registerService(new CDNUploadService());
    }

    private void registerService(Service service) {
        this.services.put(service.getClass(), service);
    }

    public void handleException(Service service, String event, Throwable throwable) {
        LOGGER.error(I18n.message("service.exception", event, service.getClass().getSimpleName()));
        LOGGER.catching(throwable);
    }

    public <I extends Service> I getService(Class<I> type) {
        return type.cast(this.services.get(type));
    }

    public void fireBootstrap(ConfigurationSection config) {
        for (var service : this.services.values()) {
            try {
                service.handleBootstrap(config);
            } catch (Throwable t) {
                this.handleException(service, "SV_BOOTSTRAP", t);
            }
        }
    }

    public void fireServerClose() {
        for (var service : this.services.values()) {
            try {
                service.handleServerClose();
            } catch (Throwable t) {
                this.handleException(service, "SV_CLOSE", t);
            }
        }
    }

    public void fireNetworkBootstrap(NettyChannelInitializer initializer) {
        for (var service : this.services.values()) {
            try {
                service.handleNetworkBootstrap(initializer);
            } catch (Throwable t) {
                this.handleException(service, "NET_BOOTSTRAP", t);
            }
        }
    }

    public void fireNetworkMessage(Packet packet, ChannelHandlerContext ctx) {
        for (var service : this.services.values()) {
            try {
                service.handleNetworkMessage(packet, ctx);
            } catch (Throwable t) {
                this.handleException(service, "NET_MESSAGE", t);
            }
        }
    }

    public boolean fireCommand(String s, String[] args) {
        for (var service : this.services.values()) {
            try {
                if(service.handleCommand(s, args)){
                    return true;
                }
            } catch (Throwable t) {
                this.handleException(service, "COMMAND", t);
            }
        }

        return false;
    }
}
