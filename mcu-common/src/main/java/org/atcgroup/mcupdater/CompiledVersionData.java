package org.atcgroup.mcupdater;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

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

    public CompiledVersionData(ByteBuf buffer) {
        this.channel = BufferUtil.readString(buffer);
        this.channelDisplayName = BufferUtil.readString(buffer);
        this.headTimeStamp = buffer.readLong();

        var logLen = buffer.readInt();
        for (var j = 0; j < logLen; j++) {
            addElapsedUpdateLog(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        }
    }

    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.channel);
        BufferUtil.writeString(buffer, this.channelDisplayName);
        buffer.writeLong(this.headTimeStamp);
        buffer.writeInt(this.elapsedUpdateLogs.size());
        for (Map.Entry<String, String> entry : this.elapsedUpdateLogs.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            BufferUtil.writeString(buffer, entry.getValue());
        }
    }

    public String getChannel() {
        return channel;
    }

    public void addElapsedUpdateLog(String version, String log) {
        this.elapsedUpdateLogs.put(version, log);
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
}
