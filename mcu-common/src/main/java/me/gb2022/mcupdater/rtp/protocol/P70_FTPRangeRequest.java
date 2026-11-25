package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

/** Receiver -> sender: request one fixed-MTU part. Its offset is partId * FILE_MTU. */
public final class P70_FTPRangeRequest implements Packet {
    private final String taskId;
    private final int partId;

    public P70_FTPRangeRequest(String taskId, int partId) {
        RTPProtocol.validateTaskId(taskId);
        if (partId < 0) {
            throw new IllegalArgumentException("Invalid part id");
        }
        this.taskId = taskId;
        this.partId = partId;
    }

    @DeserializedConstructor
    public P70_FTPRangeRequest(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer), buffer.readInt());
        RTPProtocol.requireEnd(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.taskId);
        buffer.writeInt(this.partId);
    }

    public String getTaskId() {
        return this.taskId;
    }

    public int getPartId() {
        return this.partId;
    }
}
