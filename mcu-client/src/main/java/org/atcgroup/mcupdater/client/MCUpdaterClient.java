package org.atcgroup.mcupdater.client;

import org.atcgroup.mcupdater.client.network.ClientNetworkService;
import org.atcgroup.mcupdater.client.ui.MainWindow;
import org.atcgroup.mcupdater.client.ui.screen.*;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.util.AsyncLock;
import org.atcraftmc.updater.client.util.Log;

public final class MCUpdaterClient {
    public static final MCUpdaterClient INSTANCE = new MCUpdaterClient();

    private final ClientInstallationInfo info = new ClientInstallationInfo();
    private final Config config = new Config();
    private final ClientNetworkService networkService = new ClientNetworkService(this);
    private final MainWindow window = new MainWindow(this);
    private final ProcessScreen processScreen = new ProcessScreen();
    private final AsyncLock lock = new AsyncLock();

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

    public void dispose() {
        this.window.dispose();
        this.networkService.shutdown();
        this.lock.resume();
    }


    //client reaction
    public void handleConnected(P01_ServerHello message) {
        Log.info("server: " + message);

        if (this.info.isInvalid()) {
            this.window.setScreen(new WelcomeScreen(this::requestConfig));
            return;
        }

        if (this.config.bool("waiting", true)) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        this.startUpdate();
    }

    public void handleUpdaterHeaderReceived(P21_VersionHeaders headers) {
        this.processScreen.setTitle("正在分析信息");
        this.processScreen.setUnsureProcess("正在分析资源变更...");

        var versions = headers.getVersions();

        this.processScreen.setTitle("正在下载资源");
        this.processScreen.setUnsureProcess("准备中...");

        this.networkService.write(new P30_FileDownloadRequest(headers.getFileList()));
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

        System.out.println("done");
    }
}
