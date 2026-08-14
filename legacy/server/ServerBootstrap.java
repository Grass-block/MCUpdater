package org.atcraftmc.updater.server;

public interface ServerBootstrap {
    static void main(String[] args) {
        new MCUpdaterServer().init();
    }
}
