package me.gb2022.mcupdater.client.ui;

import com.formdev.flatlaf.FlatLightLaf;
import me.gb2022.mcupdater.UpdateHeader;
import me.gb2022.mcupdater.client.ClientConfig;
import me.gb2022.mcupdater.client.ClientError;
import me.gb2022.mcupdater.client.ui.desktop.MainWindow;
import me.gb2022.mcupdater.client.ui.desktop.NotificationService;
import me.gb2022.mcupdater.client.ui.desktop.screen.*;
import me.gb2022.mcupdater.data.ChannelMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DesktopClientUI extends ClientUI {
    public static final Logger LOGGER = LogManager.getLogger("MCU/DesktopUI");

    private final MainWindow window = new MainWindow(this);
    private final ProcessScreen processScreen = new ProcessScreen();
    private boolean disposed = false;

    @Override
    public void init() {
        try {
            JFrame.setDefaultLookAndFeelDecorated(true);
            UIManager.setLookAndFeel(new FlatLightLaf());

            var properties = new Properties();
            //properties.load(Main.class.getResourceAsStream("/theme.properties"));
            for (var key : properties.keySet()) {
                UIManager.put(key, Color.decode(properties.getProperty(key.toString())));
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to initialize LaF", ex);
        }

        this.window.init();
        this.window.setScreen(new StartScreen(this.getClient()));
    }

    @Override
    public void handleUpdateStarted() {
        this.window.setScreen(this.processScreen);
        this.processScreen.setUnsureProgress("准备中...");
        this.processScreen.setUnsureProgress("正在下载资源变更");
    }

    @Override
    public boolean handleServerConnected() {
        if (this.getClient().getLocalVersionInfo().isInvalid()) {
            try {
                this.window.setScreen(new WelcomeScreen(this.getClient()::requestConfig));
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return false;
        }

        this.processScreen.setUnsureProgress("即将启动更新...");
        this.processScreen.setUnsureProgress("若要修改配置，请在3s内按下 [K]; 若要跳过请按下 [Space]");
        this.window.setScreen(this.processScreen);

        var skip = new AtomicBoolean(false);
        var interrupt = new AtomicBoolean(false);
        var listener = new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_K) {
                    interrupt.set(true);
                }
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    skip.set(true);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        };

        this.window.getHandle().addKeyListener(listener);

        if (this.getClient().getConfig().bool("waiting", true)) {
            for (var a = 0; a < 60; a++) {
                try {
                    Thread.sleep(50);
                    if (skip.get()) {
                        this.window.getHandle().removeKeyListener(listener);
                        break;
                    }
                    if (interrupt.get()) {
                        this.window.getHandle().removeKeyListener(listener);
                        this.getClient().requestConfig();
                        return false;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        this.window.getHandle().removeKeyListener(listener);
        return false;
    }

    @Override
    public void handleClientError(ClientError error, Throwable t) {
        var message = switch (error) {
            case NETWORK -> "网络错误，更新服务器可能开小差去了...";
            case CONFIG -> "MCUpdater配置信息残缺或文件损坏，请重新安装整个客户端。";
            default -> "发生了未知错误，请联系服务器管理员。";
        };

        this.window.setScreen(new ErrorScreen(message,this.getClient()));
    }

    @Override
    public void handleMainProgressReport(String title, long work, long total) {
        this.processScreen.setTitle(title);
        this.processScreen.setProgress((int) (work / (float) total * 100));
    }

    @Override
    public void handleConfiguration(Set<ChannelMetadata> metadata) {
        var i = this.getClient().getLocalVersionInfo();
        this.window.setScreen(new InstallConfigScreen(metadata, i, this.getClient()::requestUpdate));
    }


    @Override
    public void handleSubProgressReport(String holder, String title, long work, long total) {
        //todo: sub-progress
    }

    @Override
    public void handleUpdateComplete(UpdateHeader header) {
        if (header.getVersions().isEmpty()) {
            this.dispose();
            NotificationService.getInstance().notify("客户端暂无更新", "客户端资源暂无变更，游戏即将启动 :D");
            LOGGER.info("No changes happened, disposed client.");
            return;
        }

        this.window.setScreen(new UpdateLogScreen(header.getVersions()));
        NotificationService.getInstance().notify("客户端更新完成", "客户端资源更新完成，游戏即将启动 :D");
    }

    @Override
    public void removeSubProgressReport(String uuid) {

    }

    public void attemptClose() {
        if (this.disposed) {
            return;
        }

        var options = new String[]{"取消", "退出游戏", "继续启动"};

        int result = JOptionPane.showOptionDialog(
                this.window.getHandle(),
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
            this.dispose();
        }
    }

    public void dispose() {
        this.disposed = true;
        this.window.getHandle().dispose();
    }

    @Override
    public void handleClientConfigLoaded(ClientConfig config) {
        this.window.title();
    }
}
