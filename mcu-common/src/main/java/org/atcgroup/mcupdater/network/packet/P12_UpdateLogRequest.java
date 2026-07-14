package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashSet;
import java.util.Set;

public final class P12_UpdateLogRequest implements Packet {
    private final Set<String> versions;

    public P12_UpdateLogRequest(Set<String> versions) {
        this.versions = versions;
    }

    @DeserializedConstructor
    public P12_UpdateLogRequest(ByteBuf buffer) {
        this.versions = new HashSet<>();

        var length = buffer.readInt();

        for (var i = 0; i < length; i++) {
            var channel =

                    this.versions.add(BufferUtil.readString(buffer));
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.versions.size());

        for (var version : this.versions) {
            BufferUtil.writeString(buffer, version);
        }
    }

    public Set<String> getVersions() {
        return versions;
    }
}
