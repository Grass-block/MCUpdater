package org.atcgroup.mcupdater.server.v4.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

/** COMPLETED is reported only after hash verification and final commit. */
public final class P75_FTPStatusResult implements Packet {
    public enum Status {
        RECEIVING, VERIFYING, COMMITTING, COMPLETED, CANCELLED, TIMED_OUT, FAILED, UNKNOWN
    }

    private final String taskId;
    private final Status status;

    public P75_FTPStatusResult(String taskId, Status status) {
        RTPProtocol.validateTaskId(taskId);
        this.taskId = taskId;
        this.status = java.util.Objects.requireNonNull(status, "status");
    }

    @DeserializedConstructor
    public P75_FTPStatusResult(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer), readStatus(buffer));
        RTPProtocol.requireEnd(buffer);
    }

    private static Status readStatus(ByteBuf buffer) {
        int status = buffer.readUnsignedByte();
        if (status >= Status.values().length) {
            throw new IllegalArgumentException("Unknown transfer status");
        }
        return Status.values()[status];
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.taskId);
        buffer.writeByte(this.status.ordinal());
    }

    public String getTaskId() {
        return this.taskId;
    }

    public Status getStatus() {
        return this.status;
    }
}
