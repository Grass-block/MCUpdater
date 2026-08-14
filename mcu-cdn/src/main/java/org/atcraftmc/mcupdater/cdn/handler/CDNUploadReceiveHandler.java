package org.atcraftmc.mcupdater.cdn.handler;

import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.handler.FileReceiveHandler;
import org.atcgroup.mcupdater.network.packet.P70_FTPHeader;
import org.atcgroup.mcupdater.util.FilePath;
import org.atcraftmc.mcupdater.cdn.FileStatusManager;

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
        return new File(FilePath.runtime() + "/" + header.getUser() + "/_cache/" + header.getFilename());
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
        var dest = new File(FilePath.runtime() + "/" + getCurrentUser() + "/" + file.getName());
        LOGGER.info("Completed file receive: {}", file.getName());
        this.fileManager.queueFileMerge(user, dest, file);
    }
}
