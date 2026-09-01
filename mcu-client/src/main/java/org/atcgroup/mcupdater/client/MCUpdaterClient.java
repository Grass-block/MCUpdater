package org.atcgroup.mcupdater.client;

import org.atcgroup.mcupdater.client.download.DownloadResolver;
import org.atcgroup.mcupdater.client.download.DownloadResult;
import org.atcgroup.mcupdater.client.network.ClientNetworkService;
import org.atcgroup.mcupdater.client.ui.MainWindow;
import org.atcgroup.mcupdater.client.ui.screen.*;
import org.atcgroup.mcupdater.client.util.Log;
import org.atcgroup.mcupdater.client.util.NotificationService;
import org.atcgroup.mcupdater.data.ServerMeta;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.data.VersionSet;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.util.AsyncLock;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MCUpdaterClient {
    public static final MCUpdaterClient INSTANCE = new MCUpdaterClient();
    public static final ExecutorService BACKGROUND_EXEC = Executors.newFixedThreadPool(12);


    private final ClientInstallationInfo info = new ClientInstallationInfo();
    private final Config config = new Config();
    private final ClientNetworkService networkService = new ClientNetworkService(this);
    private final MainWindow window = new MainWindow(this);
    private final ProcessScreen processScreen = new ProcessScreen();
    private final AsyncLock lock = new AsyncLock();
    private final DownloadResult downloadResult = new DownloadResult();
    private ServerMeta serverMeta;
    private Set<VersionInfo> targetVersions;
    private VersionSet targetVersionRecord;
    private boolean isErrored = false;

    public static MCUpdaterClient instance() {
        return INSTANCE;
    }

    //client action
    public void run() {
        this.window.init();
        this.window.setScreen(new StartScreen());

        if (!this.config.load()) {
            this.handleException(ClientError.CONFIG, new RuntimeException());
            return;
        }

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
        Log.info("server: " + message);
        this.serverMeta = message.getServerMeta();

        if (this.info.isInvalid()) {
            this.window.setScreen(new WelcomeScreen(this::requestConfig));
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
        this.targetVersions = headers.getVersions();
        this.targetVersionRecord = headers.getVersionSet();

        this.processScreen.setTitle("正在下载资源");
        for (var resolver : DownloadResolver.RESOLVERS) {
            resolver.resolve(headers, this.downloadResult);
        }
    }

    public void handleResourceDownloadComplete() {
        if(this.isErrored) {
            return;
        }
        this.processScreen.setActive(false);
        this.downloadResult.sync(this.processScreen);

        this.processScreen.setUnsureProgress("正在更新本地资源...");

        var mergedDeletes = new ArrayList<String>();

        for (var v : this.targetVersions) {
            mergedDeletes.addAll(v.getDeleteFileList());
        }

        var counter = 1;
        for (var s : mergedDeletes) {
            var a = mergedDeletes.size();
            var p = (int) (counter / (float) a * 100);

            this.processScreen.setUnsureProgress("正在移除旧版文件 [%s/%s - %s%%] : %s".formatted(counter, a, p, s));

            counter++;
        }

        this.downloadResult.complete(this.processScreen);

        Log.info("Update complete.");
        completeUpdate();
    }

    public void completeUpdate() {
        if(this.isErrored) {
            return;
        }
        for (var v : this.targetVersions) {
            this.info.setTime(v.getChannel(), v.getTimestamp());
        }

        this.info.save();

        if (this.targetVersionRecord.isEmpty() || this.targetVersionRecord.values().stream().allMatch(Set::isEmpty)) {
            this.networkService.shutdown();
            this.dispose();
            NotificationService.getInstance().notify("客户端暂无更新", "客户端资源暂无变更，游戏即将启动 :D");
            Log.info("No changes happened, disposed client.");
            return;
        }

        this.lock.resume();
        NotificationService.getInstance().notify("客户端更新完成", "客户端资源更新完成，游戏即将启动 :D");

        Log.info("Successfully booted game thread, requesting update log from server.");
        this.networkService.write(new P12_UpdateLogRequest(this.targetVersionRecord));
    }

    public void handleLogReceived(P22_UpdateLogs message) {
        if(this.isErrored) {
            return;
        }
        this.networkService.shutdown();
        this.window.setScreen(new UpdateLogScreen(message));
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
}
