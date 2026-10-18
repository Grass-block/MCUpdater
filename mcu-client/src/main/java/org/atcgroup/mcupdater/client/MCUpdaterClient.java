package org.atcgroup.mcupdater.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.client.download.DownloadResolver;
import org.atcgroup.mcupdater.client.download.DownloadResult;
import org.atcgroup.mcupdater.client.network.ClientNetworkService;
import org.atcgroup.mcupdater.client.ui.MainWindow;
import org.atcgroup.mcupdater.client.ui.screen.*;
import org.atcgroup.mcupdater.client.util.NotificationService;
import org.atcgroup.mcupdater.data.ServerMeta;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.util.AsyncLock;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MCUpdaterClient {
    public static final Logger LOGGER = LogManager.getLogger("MCU/Client");
    public static final MCUpdaterClient INSTANCE = new MCUpdaterClient();
    public static final ExecutorService BACKGROUND_EXEC = Executors.newFixedThreadPool(12);

    private final ClientInstallationInfo info = new ClientInstallationInfo();
    private final Config config = new Config();
    private final ClientNetworkService networkService = new ClientNetworkService(this);
    private final MainWindow window = new MainWindow(this);
    private final ProcessScreen processScreen = new ProcessScreen();
    private final AsyncLock lock = new AsyncLock();
    private DownloadResult downloadResult;
    private ServerMeta serverMeta;
    private UpdateHeader header;
    private boolean isErrored = false;

    public static MCUpdaterClient instance() {
        return INSTANCE;
    }

    //client action
    public void run() {
        if (!this.config.load()) {
            this.window.init();
            this.handleException(ClientError.CONFIG, new RuntimeException());
            return;
        }

        this.window.init();
        this.window.setScreen(new StartScreen());

        this.info.load();
        this.networkService.run(this.config.service());
    }

    public void requestConfig() {
        this.networkService.write(new P10_ChannelHeaderRequest());
    }

    public void startUpdate() {
        this.info.save();
        this.window.setScreen(this.processScreen);
        this.processScreen.setUnsureProgress("准备中...");
        this.processScreen.setUnsureProgress("正在下载资源变更");
        this.networkService.write(new P11_UpdateRequest(this.info.getLocalVersions()));
    }

    public ProcessScreen getProcessScreen() {
        return processScreen;
    }

    public void dispose() {
        this.window.dispose();
        this.networkService.shutdown();
        this.lock.resume();
    }


    //client reaction
    public void handleConnected(P01_ServerHello message) {
        if(this.isErrored) {
            return;
        }
        LOGGER.info("server: " + message);
        this.serverMeta = message.getServerMeta();

        if (this.info.isInvalid()) {
            try {
                this.window.setScreen(new WelcomeScreen(this::requestConfig));
            }catch (Throwable e){
                e.printStackTrace();
            }
            return;
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

        if (this.config.bool("waiting", true)) {
            for (var a = 0; a < 60; a++) {
                try {
                    Thread.sleep(50);
                    if (skip.get()) {
                        this.window.getHandle().removeKeyListener(listener);
                        break;
                    }
                    if (interrupt.get()) {
                        this.window.getHandle().removeKeyListener(listener);
                        this.requestConfig();
                        return;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        this.window.getHandle().removeKeyListener(listener);

        this.startUpdate();
    }


    public void handleUpdaterHeaderReceived(P21_VersionHeaders headers) {
        if(this.isErrored) {
            return;
        }

        this.header = headers.getHeader();
        this.processScreen.setTitle("正在下载资源");
        this.downloadResult = new DownloadResult(this.header);

        for (var resolver : DownloadResolver.RESOLVERS) {
            resolver.resolve(this.header, this.downloadResult);
        }
    }

    public void handleResourceDownloadComplete() {
        if(this.isErrored) {
            return;
        }

        this.processScreen.setActive(false);
        this.downloadResult.sync(this.processScreen);

        this.processScreen.setUnsureProgress("正在更新本地资源...");
        this.downloadResult.complete(this.processScreen);

        LOGGER.info("Update complete.");
        completeUpdate();
    }

    public void completeUpdate() {
        if(this.isErrored) {
            return;
        }
        for (var v : this.header.getVersions()) {
            this.info.setTime(v.getChannel(), v.getHeadTimeStamp());
        }

        this.info.save();
        this.networkService.shutdown();

        if (this.header.getVersions().isEmpty()) {
            this.dispose();
            NotificationService.getInstance().notify("客户端暂无更新", "客户端资源暂无变更，游戏即将启动 :D");
            LOGGER.info("No changes happened, disposed client.");
            return;
        }

        this.lock.resume();
        NotificationService.getInstance().notify("客户端更新完成", "客户端资源更新完成，游戏即将启动 :D");

        this.window.setScreen(new UpdateLogScreen(this.header.getVersions()));
    }

    public void handleConfigReceived(P20_ChannelHeaders message) {
        if(this.isErrored) {
            return;
        }
        this.window.setScreen(new InstallConfigScreen(message.getMetas(), this::startUpdate));
    }

    public void handleException(ClientError eventId, Exception e) {
        var error = switch (eventId) {
            case NETWORK -> "网络错误，更新服务器可能开小差去了...";
            case CONFIG -> "MCUpdater配置信息残缺或文件损坏，请重新安装整个客户端。";
            default -> "发生了未知错误，请联系腐竹或服务器管理员。";
        };

        this.isErrored = true;

        e.printStackTrace();

        this.window.setScreen(new ErrorScreen(error));
        this.networkService.shutdown();
    }


    public ClientInstallationInfo getClientInfo() {
        return info;
    }

    public AsyncLock getLock() {
        return lock;
    }

    public void start() {
        this.lock.pause();

        new Thread(this::run, "mcu:main").start();

        this.lock.monitor();
    }

    public Config config() {
        return this.config;
    }

    public ServerMeta getServerMeta() {
        return this.serverMeta;
    }

    public ClientNetworkService getNetworkService() {
        return this.networkService;
    }

    public DownloadResult getDownloadResult() {
        return downloadResult;
    }
}
