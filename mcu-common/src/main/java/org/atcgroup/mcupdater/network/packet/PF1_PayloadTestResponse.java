package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.Random;

public final class PF1_PayloadTestResponse implements Packet {
    private final byte[] data;

    @DeserializedConstructor
    public PF1_PayloadTestResponse(ByteBuf buffer) {
        this.data = BufferUtil.readArray(buffer);
    }

    public PF1_PayloadTestResponse(int len) {
        this.data = new byte[len];
        new Random().nextBytes(this.data);
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeArray(buffer, this.data);
    }
}
