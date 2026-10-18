package org.atcgroup.mcupdater;

import com.google.gson.JsonObject;
import org.atcgroup.mcupdater.data.VersionInfo;

import java.util.*;

public final class CompiledVersionData {
    private final Map<String, String> elapsedUpdateLogs = new HashMap<>();
    private final String channel;
    private final String channelDisplayName;
    private final long headTimeStamp;

    public CompiledVersionData(String channel, String channelDisplayName, long headTimeStamp) {
        this.channel = channel;
        this.channelDisplayName = channelDisplayName;
        this.headTimeStamp = headTimeStamp;
    }

    public String getChannel() {
        return channel;
    }

    public void addElapsedUpdateLog(String version,String log){
        this.elapsedUpdateLogs.put(version,log);
    }

    public Map<String, String> getElapsedUpdateLogs() {
        return elapsedUpdateLogs;
    }

    public long getHeadTimeStamp() {
        return headTimeStamp;
    }

    public String getChannelDisplayName() {
        return channelDisplayName;
    }

    public JsonObject toJson() {
        var dom = new JsonObject();
        dom.addProperty("channel", this.channel);
        dom.addProperty("channelDisplayName", this.channelDisplayName);
        dom.addProperty("headTimeStamp", this.headTimeStamp);
        var m_logs = new JsonObject();
        for (var e : this.elapsedUpdateLogs.entrySet()) {
            m_logs.addProperty(e.getKey(), e.getValue());
        }
        dom.add("elapsedUpdateLogs", m_logs);
        return dom;
    }
}
