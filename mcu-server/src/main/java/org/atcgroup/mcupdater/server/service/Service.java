package org.atcgroup.mcupdater.server.service;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.server.MCUpdaterServer;
import org.bukkit.configuration.ConfigurationSection;

public interface Service {
    default void handleNetworkBootstrap(NettyChannelInitializer initializer) throws Throwable {
    }

    default void handleBootstrap(ConfigurationSection config) throws Throwable {
    }

    default void handleServerClose() throws Throwable {
    }

    default void handleNetworkMessage(Packet packet, ChannelHandlerContext ctx) throws Throwable {
    }

    default boolean handleCommand(String command,String[] args) {
        return false;
    }

    default <I extends Service> I getService(Class<I> type) {
        return MCUpdaterServer.instance().getServiceManager().getService(type);
    }
}
