package org.atcgroup.mcupdater.server;

import org.atcraftmc.updater.server.MCUpdaterServer;

public interface ServerBootstrap {
    static void main(String[] args) {
        new MCUpdaterServer().init();
    }
}
