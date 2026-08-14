package org.atcraftmc.updater.client;

import org.atcgroup.mcupdater.client.ClientInstallationInfo;
import org.atcgroup.mcupdater.client.Config;
import org.atcraftmc.updater.client.network.VersionChannelRequestHandler;
import org.atcraftmc.updater.client.ui.ConfiguratorUI;
import org.atcraftmc.updater.client.ui.ErrorUI;
import org.atcraftmc.updater.client.ui.MainWindowUI;
import org.atcraftmc.updater.client.ui.UpdateViewingUI;
import org.atcraftmc.updater.client.ui.framework.UIHandle;
import org.atcgroup.mcupdater.client.util.Log;
import org.atcraftmc.updater.network.packet.P10_VersionInfo;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class MCUpdaterClient {
    public static final MCUpdaterClient INSTANCE = new MCUpdaterClient();

    private final Config config = new Config();


    final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final BlockingQueue<Object> lock = new ArrayBlockingQueue<>(1);
    private final ClientInstallationInfo info = new ClientInstallationInfo();
    private final ClientNetworkService networkController;
    private UIHandle<MainWindowUI> mainWindowUI;
    private boolean completeView = false;

    public MCUpdaterClient() {
        var address = ClientBootstrap.config().service().split(":");
        var addr = address[0];
        var port = Integer.parseInt(address[1]);

        this.networkController = new ClientNetworkService(addr, port);
    }

    public Config getConfig() {
        return config;
    }

    private void requestUpdate() {
        this.info.save();
        this.mainWindowUI.ui().setCommentMessage("正在请求更新...");
        var localVersionInfoCache = this.info.getLocalVersions()
                .entrySet()
                .stream()
                .filter((e) -> e.getValue() >= 0)
                .map((e) -> new P10_VersionInfo.info(e.getKey(), "", e.getValue(), "", ""))
                .collect(Collectors.toSet());
        this.networkController.channel().writeAndFlush(new P10_VersionInfo(localVersionInfoCache));

        Log.info("started update check...");
    }

    private void requestConfiguration() {
        this.mainWindowUI.ui().setCommentMessage("等待配置完成...");

        ConfiguratorUI.open(this.info, (ui) -> {
            var handler = new VersionChannelRequestHandler(ui::acceptData);
            this.networkController.channel().pipeline().addLast(handler);
        }).setCloseCallback(this::requestUpdate);
    }

    public void run() {
        this.info.load();

        this.mainWindowUI = MainWindowUI.open((h) -> {});
        this.networkController.start();

        try {
            this.lock.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleConnectionSuccess() {
        final boolean[] request = {false};
        var listener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_F) {
                    return;
                }

                requestConfiguration();
                request[0] = true;
            }
        };

        if (this.info.isInvalid()) {
            this.mainWindowUI.frame().removeKeyListener(listener);
            requestConfiguration();
            request[0] = true;
            this.mainWindowUI.ui().setCommentMessage("未检测到本地安装设置，请配置...");
            return;
        }

        this.mainWindowUI.ui().setCommentMessage("连接成功, 5s后开始下载... (按[F]打开下载中心)");
        this.mainWindowUI.frame().addKeyListener(listener);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (request[0]) {
            return;
        }

        this.mainWindowUI.frame().removeKeyListener(listener);
        requestUpdate();
    }

    private void handleError(Throwable t) {
        this.mainWindowUI.close();
        ErrorUI.open(t);
        this.lock.add(new Object());
        ClientBootstrap.notify("客户端更新异常", "发生了一些错误。");
    }

    private void handleUpdateComplete(P10_VersionInfo v) {
        if (this.completeView) {
            return;
        }
        this.completeView = true;

        var updated = false;

        for (var vv : v.getInfos()) {
            if (this.info.getTime(vv.id()) == vv.timestamp()) {
                continue;
            }
            updated = true;
            this.info.setTime(vv.id(), vv.timestamp());
        }

        this.info.save();

        if (!updated) {
            ClientBootstrap.notify(ClientBootstrap.config().brand() + " 客户端暂无更新", "当前所有资源包为最新版本。");
            this.mainWindowUI.close();
            this.lock.add(new Object());
            return;
        }

        UpdateViewingUI.view(v.getInfos());
        ClientBootstrap.notify(ClientBootstrap.config().brand() + " 客户端更新完成", "所有资源包均已更新到最新版本。");
        this.mainWindowUI.close();
        this.lock.add(new Object());
    }

    public void callEvent(Event event, Object... args) {
        var ui = this.mainWindowUI.ui();

        switch (event) {
            case PROGRESS -> ui.setCommentMessage(args[0].toString());
            case PROGRESS_WORKING -> {
                var a = Double.parseDouble(args[1].toString());
                var b = Double.parseDouble(args[2].toString());

                ui.setProgress((int) (a / b * 100));
                ui.setCommentMessage(args[0].toString());
            }
            case CONNECT_SUCCESS -> this.handleConnectionSuccess();
            case RECEIVE_VERSION -> this.handleUpdateComplete((P10_VersionInfo) args[0]);
            case EXCEPTION -> this.handleError((Throwable) args[0]);
        }
    }
}
