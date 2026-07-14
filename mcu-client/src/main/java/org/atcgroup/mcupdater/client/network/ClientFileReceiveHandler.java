package org.atcgroup.mcupdater.client.network;

import org.atcgroup.mcupdater.client.ClientFilePath;
import org.atcgroup.mcupdater.network.handler.FileReceiveHandler;
import org.atcgroup.mcupdater.network.packet.P70_FTPHeader;
import org.atcraftmc.updater.client.util.Log;

import java.io.File;

public final class ClientFileReceiveHandler extends FileReceiveHandler {

    @Override
    public File getFile(P70_FTPHeader header) {
        return ClientFilePath.CACHE.append(header.getFilename()).file();
    }

    @Override
    public void onWriteStart(File file, P70_FTPHeader header) {
        Log.info("Starting file receive: %s(%s bytes, %s packets)".formatted(
                header.getFilename(),
                header.getTotalLength(),
                header.getTotalPackets()
        ));
    }

    @Override
    public void onWriteComplete(File file) {
        Log.info("File receive complete: %s(%s bytes)".formatted(file.getName(), file.length()));
    }
}
