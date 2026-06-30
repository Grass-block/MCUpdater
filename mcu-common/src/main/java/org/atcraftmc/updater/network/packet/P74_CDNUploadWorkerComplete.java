package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P74_CDNUploadWorkerComplete implements Packet {
    public P74_CDNUploadWorkerComplete() {
        super();
    }

    @DeserializedConstructor
    public P74_CDNUploadWorkerComplete(ByteBuf buffer) {
    }

    @Override
    public void write(ByteBuf byteBuf) {
    }
}
