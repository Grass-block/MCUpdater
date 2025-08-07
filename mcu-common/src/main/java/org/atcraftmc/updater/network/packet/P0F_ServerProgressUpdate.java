package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P0F_ServerProgressUpdate implements Packet {
    private final String data;

    public P0F_ServerProgressUpdate(String data) {
        this.data = data;
    }

    @Override
    public void write(ByteBuf byteBuf) {
        BufferUtil.writeString(byteBuf, data);
    }

    @DeserializedConstructor
    public P0F_ServerProgressUpdate(ByteBuf buffer){
        this.data = BufferUtil.readString(buffer);
    }

    public String getData() {
        return data;
    }
}
