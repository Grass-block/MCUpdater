package org.atcgroup.mcupdater.server;

import org.atcgroup.mcupdater.ProductInfo;
import org.atcgroup.mcupdater.server.service.CDNUploadService;
import org.atcgroup.mcupdater.util.I18n;

import java.util.Scanner;

import static org.atcgroup.mcupdater.server.MCUpdaterServer.LOGGER;

public final class ConsoleService {
    private boolean running = true;

    public void stop() {
        LOGGER.info(I18n.message("console.stop"));
        this.running = false;
    }

    public void start() {
        new Thread(this::run, "msu:console").start();
    }

    public void run() {
        var scanner = new Scanner(System.in);
        while (this.running) {
            var line = scanner.nextLine();
            if (line.isEmpty() || line.isBlank()) {
                continue;
            }
            LOGGER.info(I18n.message("console.input", line));
            this.handleCommand(line.split(" "));
        }
        scanner.close();
    }

    public void init() {
        for (var s : ProductInfo.logo("Server", ProductInfo.VERSION).split("\n")) {
            LOGGER.info(s);
        }
        help();
    }

    public void help() {
        LOGGER.info(I18n.message("console.help.stop"));
        LOGGER.info(I18n.message("console.help.reload"));
        LOGGER.info(I18n.message("console.help.help"));
        LOGGER.info(I18n.message("console.help.build"));
        LOGGER.info(I18n.message("console.help.cdn_upload"));
    }

    public void handleCommand(String[] input) {
        var args = new String[input.length - 1];

        System.arraycopy(input, 1, args, 0, args.length);


        switch (input[0]) {
            case "stop" -> MCUpdaterServer.instance().stop();
            case "help" -> {
                LOGGER.info(I18n.message("console.help.header"));
                help();
            }
            case "reload" -> {
                LOGGER.info(I18n.message("console.reload"));
                MCUpdaterServer.instance().loadConfiguration();
                LOGGER.info(I18n.message("console.reload.complete"));
            }
            default -> {
                if(!MCUpdaterServer.instance().getServiceManager().fireCommand(input[0],args)){
                    LOGGER.info(I18n.message("console.unknown"));
                }
            }
        }
    }
}
