package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.mcupdater.UpdateHeader;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P13_VersionHeaders implements Packet {
    private final UpdateHeader header;

    public P13_VersionHeaders(UpdateHeader header) {
        this.header = header;
    }

    @DeserializedConstructor
    public P13_VersionHeaders(ByteBuf buffer) {
        this.header = new UpdateHeader(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        this.header.write(buffer);
    }

    public UpdateHeader getHeader() {
        return header;
    }
}
