package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P64_RTPBatchRelease implements Packet {
    private final String batchId;

    public P64_RTPBatchRelease(String batchId) {
        RTPProtocol.validateTaskId(batchId);
        this.batchId = batchId;
    }

    @DeserializedConstructor
    public P64_RTPBatchRelease(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer));
        RTPProtocol.requireEnd(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.batchId);
    }

    public String getBatchId() {
        return this.batchId;
    }
}
