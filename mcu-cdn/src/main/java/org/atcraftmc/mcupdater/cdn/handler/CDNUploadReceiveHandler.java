package org.atcraftmc.mcupdater.cdn.handler;

import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.handler.FileReceiveHandler;
import org.atcgroup.mcupdater.network.packet.P70_FTPHeader;
import org.atcraftmc.mcupdater.cdn.FileStatusManager;
import org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer;

import java.io.File;

public final class CDNUploadReceiveHandler extends FileReceiveHandler {
    public static final Logger LOGGER = LogManager.getLogger("FileUploadReceiver");

    private final FileStatusManager fileManager;

    public CDNUploadReceiveHandler(FileStatusManager fileManager) {
        this.fileManager = fileManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public boolean validateConnection(String user, String token) {
        //todo
        return super.validateConnection(user, token);
    }

    @Override
    public File getFile(P70_FTPHeader header) {
        return MCUpdaterCDNServer.FILE_PATH.append(header.getUser()).append("_cache").append(header.getFilename()).file();
    }

    @Override
    public void onWriteStart(File file, P70_FTPHeader header) {
        LOGGER.info(
                "Starting file receive: {} via {} bytes, {} packets.",
                header.getFilename(),
                header.getTotalLength(),
                header.getTotalPackets()
        );
    }

    @Override
    public void onWriteComplete(String user, File file) {
        var dest = MCUpdaterCDNServer.FILE_PATH.append(user).append(file.getName()).file();
        LOGGER.info("Completed file receive: {}", file.getName());
        this.fileManager.queueFileMerge(user, dest, file);
    }
}
