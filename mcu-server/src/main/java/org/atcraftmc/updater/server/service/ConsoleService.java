package org.atcraftmc.updater.server.service;

import org.atcgroup.mcupdater.ProductInfo;
import org.atcraftmc.updater.server.MCUpdaterServer;

import java.util.Scanner;

import static org.atcraftmc.updater.server.MCUpdaterServer.LOGGER;

public final class ConsoleService extends Service {
    private boolean running = true;
    private boolean initialized = false;

    public ConsoleService(MCUpdaterServer server) {
        super(server);
    }

    public void stop() {
        LOGGER.info("正在关闭控制台服务...");
        this.running = false;
    }

    @Override
    public void run() {
        var scanner = new Scanner(System.in);
        this.init();
        while (this.running) {
            var line = scanner.nextLine();
            if (line.isEmpty() || line.isBlank()) {
                continue;
            }
            LOGGER.info(">> {}", line);
            this.handleCommand(line.split(" "));
        }
        scanner.close();
    }

    public void init() {
        for (var s : ProductInfo.logo("Server", ProductInfo.VERSION).split("\n")) {
            LOGGER.info(s);
        }
        help();
        this.initialized = true;
    }

    public void waitFor() {
        while (!this.initialized) {
            Thread.yield();
        }
    }

    public void help() {
        LOGGER.info(" - stop: 停止服务器");
        LOGGER.info(" - reload: 重新加载服务器配置");
        LOGGER.info(" - help: 显示帮助信息");
        LOGGER.info(" - build <频道> <版本>: 构建版本");
        LOGGER.info(" - cdn-upload: 上传所有本地资源包");
    }

    @Override
    public String name() {
        return "console-service";
    }

    public void handleCommand(String[] input) {
        switch (input[0]) {
            case "stop" -> server().stop();
            case "build" -> server().buildVersion(input);
            case "help" -> {
                LOGGER.info("==========[帮助信息]==========");
                help();
            }
            case "reload" -> {
                LOGGER.info("正在重新加载配置文件...");
                server().loadConfiguration();
                LOGGER.info("配置文件已经更新。网络服务需要重启才能应用修改。");
            }
            case "cdn-upload" -> {
                LOGGER.info("正在上传全部本地资源...");
                server().uploadPacks();
            }
        }
    }
}
