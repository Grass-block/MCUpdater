package org.atcgroup.mcupdater.server;

import org.atcgroup.mcupdater.server.file.FileAddHandler;
import org.atcgroup.mcupdater.util.I18n;

public interface Main {
    static void main(String[] args) {
        I18n.instance().load(Main.class.getResourceAsStream("/language.json"));
        MCUpdaterServer.instance().run();
    }
}
