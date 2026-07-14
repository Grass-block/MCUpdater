package org.atcgroup.mcupdater;

public interface ProductInfo {
    String VERSION = "2.0.32";

    static String logo(String artifact, String version) {
        return """
                 __  __   ____  _   _             _         _             \s
                 |  \\/  | / ___|| | | | _ __    __| |  __ _ | |_  ___  _ __\s
                 | |\\/| || |    | | | || '_ \\  / _` | / _` || __|/ _ \\| '__|
                 | |  | || |___ | |_| || |_) || (_| || (_| || |_|  __/| |  \s
                 |_|  |_| \\____| \\___/ | .__/  \\__,_| \\__,_| \\__|\\___||_|  \s
                                               |_|                                 \s
                 =====================================================================
                 MCUpdater-%s v%s   by GrassBlock2022@ATCraftMC
                """.formatted(artifact, version);
    }
}
