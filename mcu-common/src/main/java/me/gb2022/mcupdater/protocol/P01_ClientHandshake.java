package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P01_ClientHandshake implements Packet {
    private final String clientId;

    public P01_ClientHandshake(String clientId) {
        this.clientId = clientId;
    }

    @DeserializedConstructor
    public P01_ClientHandshake(ByteBuf buffer) {
        this.clientId = BufferUtil.readString(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, clientId);
    }

    public String getClientId() {
        return clientId;
    }
}
