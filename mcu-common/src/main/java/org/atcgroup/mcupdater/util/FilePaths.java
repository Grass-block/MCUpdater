package org.atcgroup.mcupdater.util;

import me.gb2022.commons.container.Vector;

import java.io.File;

public interface FilePaths {
    Vector<String> SERVER = new Vector<>("127.0.0.1:65320");

    static String runtime() {
        return System.getProperty("user.dir");
    }

    static String updater() {
        return runtime() + "/.updater";
    }

    static File config() {
        return new File(updater() + "/config.json");
    }

    static String server() {
        return "http://" + SERVER.get();
    }

    static String resourcePackId(String channel, String uuid) {
        return channel + "_" + uuid;
    }

    static String resourcePack(String channel, String uuid) {
        return runtime() + "/packs/" + resourcePackId(channel, uuid) + ".zip";
    }
}
