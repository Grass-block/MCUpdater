package org.atcgroup.mcupdater.client.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.client.ClientFilePath;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.client.ui.UI;
import org.atcgroup.mcupdater.network.handler.FileReceiveHandler;
import org.atcgroup.mcupdater.network.packet.P70_FTPHeader;

import java.io.File;

public final class ClientFileReceiveHandler extends FileReceiveHandler {
    public static final Logger LOGGER = LogManager.getLogger("MCU/FileReceiveHandler");


    @Override
    public File getFile(P70_FTPHeader header) {
        return ClientFilePath.CACHE.append(header.getFilename()).file();
    }

    @Override
    public void onWriteStart(File file, P70_FTPHeader header) {
        MCUpdaterClient.INSTANCE.getProcessScreen().setTitle("正在下载文件...");

        LOGGER.info(
                "Starting file receive: {}({} bytes, {} packets)",
                header.getFilename(),
                header.getTotalLength(),
                header.getTotalPackets()
        );
    }

    @Override
    public void onProcess(String name, long received, long total) {
        var r = UI.NUMBER_FORMAT.format(received / 1024f / 1024f);
        var t = UI.NUMBER_FORMAT.format(total / 1024f / 1024f);
        var p = (int) ((double) received / (double) total * 100);

        MCUpdaterClient.INSTANCE.getProcessScreen().setProgress(p);
        MCUpdaterClient.INSTANCE.getProcessScreen().setTitle("正在下载资源文件: %s - %s%% (%s/%s MiB)".formatted(name, p, r, t));
    }

    @Override
    public void onWriteComplete(String user, File file) {
        LOGGER.info("File receive complete: {}({} bytes)", file.getName(), file.length());
    }
}
