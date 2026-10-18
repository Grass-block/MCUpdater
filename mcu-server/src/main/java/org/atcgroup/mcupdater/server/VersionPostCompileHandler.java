package org.atcgroup.mcupdater.server;

import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.FileRepository;

public interface VersionPostCompileHandler {
    void handle(UpdateHeader data, FileRepository repository);
}
