package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P32_FileDownloadComplete implements Packet {
    private final String taskId;

    public P32_FileDownloadComplete(String taskId) {
        this.taskId = taskId;
    }

    @DeserializedConstructor
    public P32_FileDownloadComplete(ByteBuf buffer) {
        this.taskId = BufferUtil.readString(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.taskId);
    }

    public String getTaskId() {
        return taskId;
    }
}
