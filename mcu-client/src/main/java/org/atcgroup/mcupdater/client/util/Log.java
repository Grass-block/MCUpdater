package org.atcgroup.mcupdater.client.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public interface Log {
    static void log(String message, String level) {
        var time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.printf("[MCUpdater] [%s] [%s] %s%n", time, level, message);
    }

    static void info(String message) {
        log(message, "I");
    }

    static void warn(String message) {
        log(message, "W");
    }

    static void error(String message) {
        log(message, "E");
    }

    static void error(String message, Throwable e) {
        error(message);
        e.printStackTrace();
    }
}
