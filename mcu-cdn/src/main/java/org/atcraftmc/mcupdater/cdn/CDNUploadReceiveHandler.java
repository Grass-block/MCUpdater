package org.atcraftmc.mcupdater.cdn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.network.handler.FileReceiveHandler;
import org.atcgroup.mcupdater.network.packet.P70_FTPHeader;

import java.io.File;
import java.util.function.Function;

public final class CDNUploadReceiveHandler extends FileReceiveHandler {
    public static final Logger LOGGER = LogManager.getLogger("FileUploadReceiver");

    private final Function<String, FileRepository> repositoryProvider;

    public CDNUploadReceiveHandler(Function<String, FileRepository> provider) {
        this.repositoryProvider = provider;
    }

    @Override
    public boolean validateConnection(String user, String token) {
        var conf = MCUpdaterCDNServer.INSTANCE.config();

        if(!conf.containsKey(user)){
            LOGGER.error("REJECTED USER {}: User not found", user);
            return false;
        }

        var result = conf.get(user).equals(token);

        if(!result){
            LOGGER.error("REJECTED USER {}: Key not match", user);
            return false;
        }

        return true;
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
        this.repositoryProvider.apply(user).queueFileMerge(user, dest, file);
    }
}
