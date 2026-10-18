package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;

public final class P34_UpgradeFileDownloadComplete implements Packet {
    public P34_UpgradeFileDownloadComplete() {
    }

    @DeserializedConstructor
    public P34_UpgradeFileDownloadComplete(ByteBuf buffer) {
    }

    @Override
    public void write(ByteBuf buffer) {
    }
}
