package org.atcraftmc.mcupdater.cdn;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import me.gb2022.commons.file.FilePath;

@Plugin(id = "mc-updater-cdn")
public final class CDNVelocityLoader {

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        MCUpdaterCDNServer.FILE_PATH = FilePath.RUNTIME.append("mc-updater-cdn");
        var t = new Thread(MCUpdaterCDNServer.INSTANCE, "MCUpdater-CDN");
        t.setDaemon(true);
        t.start();
    }
}
