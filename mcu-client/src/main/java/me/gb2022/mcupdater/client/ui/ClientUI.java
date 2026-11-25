package me.gb2022.mcupdater.client.ui;

import me.gb2022.mcupdater.UpdateHeader;
import me.gb2022.mcupdater.client.ClientConfig;
import me.gb2022.mcupdater.client.ClientError;
import me.gb2022.mcupdater.client.MCUpdaterClient;
import me.gb2022.mcupdater.data.ChannelMetadata;

import java.util.Set;

public abstract class ClientUI {
    private MCUpdaterClient client;

    public abstract void init();

    public abstract void handleClientError(ClientError error, Throwable t);

    public abstract void handleConfiguration(Set<ChannelMetadata> metadata);

    public abstract void handleMainProgressReport(String title, long work, long total);

    public abstract void handleSubProgressReport(String holder, String title, long work, long total);

    public abstract void handleUpdateComplete(UpdateHeader header);

    public abstract void removeSubProgressReport(String uuid);

    public void handleUpdateStarted() {
    }

    public boolean handleServerConnected() {
        return true;
    }

    public final MCUpdaterClient getClient() {
        return client;
    }

    public final void _setContext(MCUpdaterClient client) {
        this.client = client;
    }


    public void dispose() {
    }

    public void handleClientConfigLoaded(ClientConfig config) {
    }
}


