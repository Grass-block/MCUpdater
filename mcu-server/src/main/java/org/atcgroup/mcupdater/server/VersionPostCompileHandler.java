package org.atcgroup.mcupdater.server;

import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.UpdateHeader;

public interface VersionPostCompileHandler {
    void handle(UpdateHeader data, FileRepository repository);
}
