package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P31_FileDownloadStart implements Packet {
    private final String taskId;
    private final Map<String, String> failedFiles;

    public P31_FileDownloadStart(String taskId, Map<String, String> failedFiles) {
        this.taskId = taskId;
        this.failedFiles = failedFiles;
    }

    @DeserializedConstructor
    public P31_FileDownloadStart(ByteBuf buffer) {
        this.taskId = BufferUtil.readString(buffer);
        this.failedFiles = new HashMap<>();

        var len = buffer.readInt();
        for (int i = 0; i < len; i++) {
            this.failedFiles.put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.taskId);

        buffer.writeInt(this.failedFiles.size());
        for (Map.Entry<String, String> entry : this.failedFiles.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            BufferUtil.writeString(buffer, entry.getValue());
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public Map<String, String> getFailedFiles() {
        return failedFiles;
    }
}
