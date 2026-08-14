package org.atcraftmc.updater.server.service;

import org.atcraftmc.updater.server.MCUpdaterServer;

public abstract class Service implements Runnable {
    private final MCUpdaterServer server;

    protected Service(MCUpdaterServer server) {
        this.server = server;
    }

    public final MCUpdaterServer server() {
        return server;
    }

    public final void start() {
        if(!this.async()){
            run();
            return;
        }
        new Thread(this, name()).start();
    }

    public abstract String name();

    public boolean async(){
        return true;
    }

    public void stop() {
    }
}
