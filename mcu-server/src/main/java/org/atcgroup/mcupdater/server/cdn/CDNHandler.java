package org.atcgroup.mcupdater.server.cdn;

import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.server.VersionPostCompileHandler;

public interface CDNHandler extends VersionPostCompileHandler {
    default void sync(FileRepository repository) {
    }

    @Override
    default void handle(UpdateHeader data, FileRepository repository) {
    }
}
