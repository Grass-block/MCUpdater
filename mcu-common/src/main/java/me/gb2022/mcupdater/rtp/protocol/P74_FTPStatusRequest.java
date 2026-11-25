package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P74_FTPStatusRequest implements Packet {
    private final String taskId;

    public P74_FTPStatusRequest(String taskId) {
        RTPProtocol.validateTaskId(taskId);
        this.taskId = taskId;
    }

    @DeserializedConstructor
    public P74_FTPStatusRequest(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer));
        RTPProtocol.requireEnd(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.taskId);
    }

    public String getTaskId() {
        return this.taskId;
    }
}
