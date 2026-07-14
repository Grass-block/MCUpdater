package org.atcgroup.mcupdater.client.ui;


import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.client.ui.screen.Screen;
import org.atcraftmc.updater.client.ClientBootstrap;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public final class MainWindow {
    private final JFrame frame = new JFrame();
    private final MCUpdaterClient client;
    private boolean disposed = false;

    public MainWindow(MCUpdaterClient client) {
        this.client = client;
        this.init();
    }

    public void init() {
        this.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.frame.setResizable(false);
        this.frame.setSize(960, 640);
        this.frame.setVisible(true);
        this.frame.setTitle("MCUpdater 4.0.0 - " + ClientBootstrap.config().brand());
        this.frame.setIconImage(UI.image("/icon.png"));
        this.frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                attemptClose();
            }
        });
    }

    public void attemptClose() {
        if (this.disposed) {
            return;
        }

        var options = new String[]{"取消", "退出游戏", "继续启动"};

        int result = JOptionPane.showOptionDialog(
                this.frame,
                "是否结束更新进程? \n- 若选择“退出游戏”则游戏进程将被结束, \n- 若选择“继续启动”则将启动游戏但不更新",
                "退出",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[2]   // 默认选中的按钮
        );

        if (result == 1) {
            System.exit(0);
        }
        if (result == 2) {
            this.disposed = true;
            this.client.dispose();
        }
    }

    public void setScreen(Screen screen) {
        this.frame.setContentPane(screen.$$$getRootComponent$$$());
        ((JPanel) this.frame.getContentPane()).updateUI();
    }

    public void dispose() {
        this.disposed = true;
        this.frame.dispose();
    }
}
