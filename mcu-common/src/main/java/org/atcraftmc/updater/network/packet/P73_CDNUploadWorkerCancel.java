package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

public final class P73_CDNUploadWorkerCancel implements Packet {
    public P73_CDNUploadWorkerCancel() {
        super();
    }

    @DeserializedConstructor
    public P73_CDNUploadWorkerCancel(ByteBuf buffer) {
    }

    @Override
    public void write(ByteBuf byteBuf) {
    }
}
