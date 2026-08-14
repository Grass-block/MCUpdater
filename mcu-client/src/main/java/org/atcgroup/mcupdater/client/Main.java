package org.atcgroup.mcupdater.client;

import com.formdev.flatlaf.FlatDarkLaf;
import org.atcgroup.mcupdater.client.util.ApplicationEntry;
import org.atcgroup.mcupdater.client.util.Log;
import org.atcgroup.mcupdater.client.util.NotificationService;
import org.atcgroup.mcupdater.util.FilePath;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.Properties;

public interface Main {
    @ApplicationEntry
    static void premain(String agentArgs, Instrumentation inst) {
        boot();
    }

    @ApplicationEntry
    static void main(String[] args) {
        boot();
    }

    static void boot() {
        theme();

        var file = new File(FilePath.updater() + "/mcu-client.properties");
        NotificationService.getInstance().init();

        if (!file.exists() || file.length() == 0) {
            Log.error("config file does not exist.");
        }

        MCUpdaterClient.INSTANCE.start();
    }

    static void theme() {
        try {
            JFrame.setDefaultLookAndFeelDecorated(true);

            UIManager.setLookAndFeel(new FlatDarkLaf());

            var properties = new Properties();

            properties.load(Main.class.getResourceAsStream("/theme.properties"));

            for (var key : properties.keySet()) {
                UIManager.put(key, Color.decode(properties.getProperty(key.toString())));
            }
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF");
        }
    }
}
