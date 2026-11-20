package org.atcgroup.mcupdater.server.v4.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.ArrayList;
import java.util.List;

public final class P62_RTPBatchRequest implements Packet {
    public static final int MAX_FILES = 32;
    private final String batchId;
    private final List<String> resourceIds;

    public P62_RTPBatchRequest(String batchId, List<String> resourceIds) {
        RTPProtocol.validateTaskId(batchId);
        if (resourceIds.size() > MAX_FILES || resourceIds.stream().distinct().count() != resourceIds.size()) {
            throw new IllegalArgumentException("Invalid batch size or duplicate resource IDs");
        }
        resourceIds.forEach(RTPProtocol::validateSha1);
        this.batchId = batchId;
        this.resourceIds = List.copyOf(resourceIds);
    }

    @DeserializedConstructor
    public P62_RTPBatchRequest(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer), readIds(buffer));
        RTPProtocol.requireEnd(buffer);
    }

    static List<String> readIds(ByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > MAX_FILES) throw new IllegalArgumentException("Invalid resource count");
        var ids = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) ids.add(RTPProtocol.readTaskId(buffer));
        return ids;
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.batchId);
        buffer.writeInt(this.resourceIds.size());
        this.resourceIds.forEach(id -> BufferUtil.writeString(buffer, id));
    }

    public String getBatchId() { return this.batchId; }
    public List<String> getResourceIds() { return this.resourceIds; }
}
