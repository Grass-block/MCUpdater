package me.gb2022.mcupdater.server;

import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.storage.CASResourceStorage;

public class MCUpdaterServer {
    private final CASResourceStorage storage;
    private final ResourceServer server;

    public MCUpdaterServer() {
        this.storage = new CASResourceStorage(FilePath.RUNTIME.append("/resource"));
        this.server = new ResourceServer(this.storage);
    }

    public void start(){

    }
}
