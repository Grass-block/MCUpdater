package org.atcgroup.mcupdater.client;

import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.client.network.CDNClient;
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
import org.atcgroup.mcupdater.util.FilePath;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MCUpdaterClient {
    public static final MCUpdaterClient INSTANCE = new MCUpdaterClient();

    private final ClientInstallationInfo info = new ClientInstallationInfo();
    private final Config config = new Config();
    private final ClientNetworkService networkService = new ClientNetworkService(this);
    private final MainWindow window = new MainWindow(this);
    private final ProcessScreen processScreen = new ProcessScreen();
    private final AsyncLock lock = new AsyncLock();
    private ServerMeta serverMeta;
    private Set<VersionInfo> targetVersions;
    private VersionSet targetVersionRecord;

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
        this.processScreen.setTitle("正在更新信息");
        this.processScreen.setUnsureProcess("正在等待服务器构建版本信息...");
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
        Log.info("server: " + message);
        this.serverMeta = message.getServerMeta();

        if (this.info.isInvalid()) {
            this.window.setScreen(new WelcomeScreen(this::requestConfig));
            return;
        }

        this.processScreen.setUnsureProcess("即将启动更新...");
        this.processScreen.setUnsureProcess("若要修改配置，请在3s内按下 [K]; 若要跳过请按下 [Space]");
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
        this.targetVersions = headers.getVersions();
        this.targetVersionRecord = headers.getVersionSet();

        this.processScreen.setUnsureProcess("准备中...");
        this.processScreen.setUnsureProcess("正在下载资源变更");

        this.processScreen.setTitle("正在下载资源");

        if (!this.serverMeta.hasCDNInfo()) {
            this.processScreen.setUnsureProcess("正在初始化下载进程...");
            this.networkService.write(new P30_FileDownloadRequest(headers.getFileList()));
            return;
        }

        var addr = new InetSocketAddress(this.serverMeta.getCdnHost(), this.serverMeta.getCdnPort());

        new CDNClient(this, addr, this.serverMeta.getCdnRepository(), headers.getFileList(), (f) -> {
            if (f == null) {
                this.handleException(ClientError.OTHER, new NullPointerException());
            }
            this.processScreen.setUnsureProcess("正在初始化下载进程...");
            this.networkService.write(new P30_FileDownloadRequest(f));
        }).run();
    }

    public void handleResourceDownloadComplete() {
        this.processScreen.setUnsureProcess("正在更新本地资源...");

        var mergedDeletes = new ArrayList<String>();
        var mergedExtracts = new ArrayList<String>();

        for (var v : this.targetVersions) {
            mergedDeletes.addAll(v.getDeleteFileList());
            mergedExtracts.addAll(v.getDownloadPackList());
        }

        var counter = 1;
        for (var s : mergedDeletes) {
            var a = mergedDeletes.size();
            var p = (int) (counter / (float) a * 100);

            this.processScreen.setUnsureProcess("正在移除旧版文件 [%s/%s - %s%%] : %s".formatted(counter, a, p, s));

            counter++;
        }

        counter = 1;
        for (var s : mergedExtracts) {
            var file = ClientFilePath.CACHE.append(s).file();
            var a0 = mergedExtracts.size();

            int finalCounter = counter;
            PatchFile.unzip(file, FilePath.runtime(), (c, a) -> {
                var p = (int) (c / (float) a * 100);
                this.processScreen.setUnsureProcess("正在解压资源包 第(%s/%s个) [%s/%s - %s%%]".formatted(finalCounter, a0, c, a, p));
            });

            counter++;
        }

        Log.info("Update complete.");
        completeUpdate();
    }

    public void completeUpdate() {
        for (var v : this.targetVersions) {
            this.info.setTime(v.getChannel(), v.getTimestamp());
        }

        this.info.save();

        if (this.targetVersionRecord.isEmpty() || this.targetVersionRecord.values().stream().allMatch(Set::isEmpty)) {
            this.dispose();
            NotificationService.getInstance().notify("客户端暂无更新", "客户端资源暂无变更，游戏即将启动 :D");
            Log.info("No changes happened, disposed client.");
            return;
        }

        this.networkService.shutdown();
        this.lock.resume();

        NotificationService.getInstance().notify("客户端更新完成", "客户端资源更新完成，游戏即将启动 :D");

        Log.info("Successfully booted game thread, requesting update log from server.");
        Log.info("TODO: log viewing is in progress.");
        this.networkService.write(new P12_UpdateLogRequest(this.targetVersionRecord));
    }

    public void handleLogReceived(P22_UpdateLogs message) {
        this.window.setScreen(new UpdateLogScreen(message));
    }

    public void handleConfigReceived(P20_ChannelHeaders message) {
        this.window.setScreen(new InstallConfigScreen(message.getMetas(), this::startUpdate));
    }

    public void handleException(ClientError eventId, Exception e) {
        var error = switch (eventId) {
            case NETWORK -> "网络错误，更新服务器可能开小差去了...";
            case CONFIG -> "MCUpdater配置信息残缺或文件损坏，请重新安装整个客户端。";
            default -> "发生了未知错误，请联系腐竹或服务器管理员。";
        };

        e.printStackTrace();

        this.window.setScreen(new ErrorScreen(error));
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
}
