package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.data.VersionSet;

public final class P12_UpdateLogRequest implements Packet {
    private final VersionSet versionSet;

    public P12_UpdateLogRequest(final VersionSet versionSet) {
        this.versionSet = versionSet;
    }


    @DeserializedConstructor
    public P12_UpdateLogRequest(ByteBuf buffer) {
        this.versionSet = new VersionSet(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        this.versionSet.write(buffer);
    }

    public VersionSet getVersions() {
        return versionSet;
    }
}
