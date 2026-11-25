package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

/** Client has registered this task; the server may now send INLINE or serve P70. */
public final class P71_FTPInlineRequest implements Packet {
    private final String taskId;
    public P71_FTPInlineRequest(String taskId) {
        RTPProtocol.validateTaskId(taskId);
        this.taskId = taskId;
    }
    @DeserializedConstructor
    public P71_FTPInlineRequest(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer));
        RTPProtocol.requireEnd(buffer);
    }
    @Override
    public void write(ByteBuf buffer) { BufferUtil.writeString(buffer, this.taskId); }
    public String getTaskId() { return this.taskId; }
}
