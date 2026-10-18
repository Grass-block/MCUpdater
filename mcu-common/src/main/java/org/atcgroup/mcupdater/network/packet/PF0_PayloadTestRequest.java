package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class PF0_PayloadTestRequest implements Packet {
    private final int len;

    @DeserializedConstructor
    public PF0_PayloadTestRequest(ByteBuf buffer) {
        this.len = buffer.readInt();
    }

    public PF0_PayloadTestRequest(int len) {
        this.len = len;
    }

    @Override
    public void write(ByteBuf byteBuf) {
        byteBuf.writeInt(len);
    }

    public int getLen() {
        return len;
    }
}
