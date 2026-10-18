package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P32_FileDownloadComplete implements Packet {
    @DeserializedConstructor
    public P32_FileDownloadComplete(ByteBuf buffer) {
    }

    public P32_FileDownloadComplete() {

    }

    @Override
    public void write(ByteBuf buffer) {
    }

}
