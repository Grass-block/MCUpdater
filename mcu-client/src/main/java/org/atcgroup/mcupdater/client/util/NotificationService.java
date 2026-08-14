package org.atcgroup.mcupdater.client.util;

import org.atcgroup.mcupdater.client.ui.UI;

import java.awt.*;
import java.util.Objects;

public final class NotificationService {
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
        this.icon = new TrayIcon(Objects.requireNonNull(UI.image("/tray.png")), "MCUpdater", new PopupMenu());

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

        this.icon.displayMessage(title, message, TrayIcon.MessageType.NONE);
    }
}
