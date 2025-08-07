package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P14_PatchFileSlice implements Packet {
    public static final byte SIG_END = 127;
    private final byte[] data;
    private final byte sig;

    public P14_PatchFileSlice(byte[] data, int sig) {
        this.data = data;
        this.sig = (byte) sig;
    }

    @DeserializedConstructor
    public P14_PatchFileSlice(ByteBuf buffer) {
        this.sig = buffer.readByte();
        this.data = BufferUtil.readArray(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeByte(this.sig);
        BufferUtil.writeArray(buffer, this.data);
    }

    public byte[] getData() {
        return data;
    }

    public int sig() {
        return sig;
    }
}
