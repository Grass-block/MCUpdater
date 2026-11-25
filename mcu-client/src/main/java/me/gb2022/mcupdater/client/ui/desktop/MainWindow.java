package me.gb2022.mcupdater.client.ui.desktop;

import me.gb2022.mcupdater.client.ui.DesktopClientUI;
import me.gb2022.mcupdater.client.ui.desktop.screen.Screen;
import org.atcgroup.mcupdater.client.MCUpdaterClient;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class MainWindow {
    private final JFrame frame = new JFrame();
    private final DesktopClientUI context;

    public MainWindow(DesktopClientUI context) {
        this.context = context;
    }

    public void init() {
        this.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.frame.setResizable(false);
        this.frame.setSize(960, 640);
        this.frame.setVisible(true);
        this.title();
        this.frame.setIconImage(UI.image("/icon.png"));
        this.frame.setLocationRelativeTo(null);
        this.frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                context.attemptClose();
            }
        });
    }

    public void setScreen(Screen screen) {
        this.frame.setContentPane(screen.$$$getRootComponent$$$());
        ((JPanel) this.frame.getContentPane()).updateUI();
    }

    public JFrame getHandle(){
        return this.frame;
    }

    public void dispose(){
        this.frame.dispose();
    }

    public void title(){
        this.frame.setTitle("MCUpdater 4.0.0_BETA - " + this.context.getClient().getConfig().brand());
    }
}
