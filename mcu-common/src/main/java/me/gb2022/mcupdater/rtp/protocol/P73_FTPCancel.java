package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P73_FTPCancel implements Packet {
    private final String taskId;

    public P73_FTPCancel(String taskId) {
        if (taskId == null || taskId.isEmpty() || taskId.length() > 128) {
            throw new IllegalArgumentException("Invalid task id");
        }
        this.taskId = taskId;
    }

    @DeserializedConstructor
    public P73_FTPCancel(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer));
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Unexpected cancel payload");
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.taskId);
    }

    public String getTaskId() {
        return this.taskId;
    }
}
