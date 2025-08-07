package org.atcraftmc.updater.client;

import com.formdev.flatlaf.FlatDarkLaf;
import me.gb2022.commons.container.Vector;
import org.atcraftmc.updater.FilePath;
import org.atcraftmc.updater.client.util.ApplicationEntry;
import org.atcraftmc.updater.client.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Properties;

public interface ClientBootstrap {
    Vector<ClientConfig> CLIENT_CONFIG = new Vector<>(null);

    @ApplicationEntry
    static void premain(String agentArgs, Instrumentation inst) {
        boot();
    }

    @ApplicationEntry
    static void main(String[] args) {
        boot();
    }

    static void boot() {
        var file = new File(FilePath.updater() + "/mcu-client.properties");
        NotificationService.getInstance().init();

        if (!file.exists() || file.length() == 0) {
            Log.error("config file does not exist.");
            error("发生错误", "没有找到客户端配置文件!");
        }

        var config = new Properties();
        try (var in = new FileInputStream(file)) {
            config.load(in);
        } catch (IOException e) {
            Log.error("cannot read config file", e);
            error("发生错误", "无法读取客户端配置文件");
        }

        CLIENT_CONFIG.set(new ClientConfig(
                config.getProperty("brand"),
                config.getProperty("service")
        ));

        Log.info("client-brand: " + config().brand());
        Log.info("service: " + config().service());

        theme();
        MCUpdaterClient.INSTANCE.run();
        NotificationService.getInstance().dispose();
    }

    static void notify(String title, String message) {
        NotificationService.getInstance().notify(title, message);
    }

    static void theme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());

            var properties = new Properties();

            properties.load(ClientBootstrap.class.getResourceAsStream("/theme.properties"));

            for (var key : properties.keySet()) {
                UIManager.put(key, Color.decode(properties.getProperty(key.toString())));
            }
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF");
        }
    }

    static ClientConfig config() {
        return CLIENT_CONFIG.get();
    }

    static void error(String title, String desc) {
        JOptionPane.showMessageDialog(null, desc, title, JOptionPane.ERROR_MESSAGE);
    }

    class NotificationService {
        private static final NotificationService instance = new NotificationService();
        private SystemTray systemTray;
        private TrayIcon icon;

        public static NotificationService getInstance() {
            return instance;
        }

        public void init() {
            if (!SystemTray.isSupported()) {
                return;
            }

            this.systemTray = SystemTray.getSystemTray();
            this.icon = new TrayIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "Updater", new PopupMenu());

            try {
                this.systemTray.add(this.icon);
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }

        public void dispose() {
            if (!SystemTray.isSupported()) {
                return;
            }

            this.systemTray.remove(this.icon);
        }

        public void notify(String title, String message) {
            if (!SystemTray.isSupported()) {
                return;
            }

            this.icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }
}
