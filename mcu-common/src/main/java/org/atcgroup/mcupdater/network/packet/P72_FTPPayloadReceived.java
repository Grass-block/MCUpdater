package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P72_FTPPayloadReceived implements Packet {
    private final long id;

    public P72_FTPPayloadReceived(long id) {
        this.id = id;
    }

    @DeserializedConstructor
    public P72_FTPPayloadReceived(ByteBuf buffer){
        this.id = buffer.readLong();
    }

    public long getId() {
        return id;
    }

    @Override
    public void write(ByteBuf byteBuf) {
        byteBuf.writeLong(this.id);
    }
}
