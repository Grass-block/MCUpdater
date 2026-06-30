package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P75_CDNUploadWorkerCompleteResponse implements Packet {
    private final boolean success;

    public P75_CDNUploadWorkerCompleteResponse(boolean success) {
        this.success = success;
    }

    @DeserializedConstructor
    public P75_CDNUploadWorkerCompleteResponse(ByteBuf buffer) {
        this.success = buffer.readBoolean();
    }

    @Override
    public void write(ByteBuf byteBuf) {
        byteBuf.writeBoolean(this.success);
    }

    public boolean isSuccess() {
        return success;
    }
}
