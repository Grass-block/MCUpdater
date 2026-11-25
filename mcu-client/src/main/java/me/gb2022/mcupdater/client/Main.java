package me.gb2022.mcupdater.client;

import me.gb2022.mcupdater.client.ui.DesktopClientUI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import me.gb2022.mcupdater.client.util.ApplicationEntry;
import me.gb2022.mcupdater.client.ui.desktop.NotificationService;
import org.atcgroup.mcupdater.util.FilePaths;

import java.io.File;
import java.lang.instrument.Instrumentation;

public interface Main {
    Logger LOGGER = LogManager.getLogger("MCU/Main");

    @ApplicationEntry
    static void premain(String agentArgs, Instrumentation inst) {
        boot();
    }

    @ApplicationEntry
    static void main(String[] args) {
        boot();
    }

    static void boot() {
        var ui = new DesktopClientUI();

        var file = new File(FilePaths.updater() + "/mcu-client.properties");
        NotificationService.getInstance().init();

        if (!file.exists() || file.length() == 0) {
            LOGGER.error("config file does not exist.");
        }

        var client = new MCUpdaterClient(ui);
        client.start();
    }
}
