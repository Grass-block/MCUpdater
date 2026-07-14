package org.atcgroup.mcupdater.server;

public interface Main {
    static void main(String[] args) {
        MCUpdaterServer.instance().run();
    }
}
