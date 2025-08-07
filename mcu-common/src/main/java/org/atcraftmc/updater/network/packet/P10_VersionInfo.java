package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashSet;
import java.util.Set;

public final class P10_VersionInfo implements Packet {
    private final Set<info> infos = new HashSet<>();

    public P10_VersionInfo(Set<info> infos) {
        this.infos.addAll(infos);
    }

    @DeserializedConstructor
    public P10_VersionInfo(ByteBuf buf) {
        var size = buf.readInt();

        for (int i = 0; i < size; i++) {
            var id = BufferUtil.readString(buf);
            var version = BufferUtil.readString(buf);
            var timestamp = buf.readLong();
            var name = BufferUtil.readString(buf);
            var desc = BufferUtil.readString(buf);
            this.infos.add(new info(id, version, timestamp, name, desc));
        }
    }


    public Set<info> getInfos() {
        return infos;
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.infos.size());

        for (info info : this.infos) {
            BufferUtil.writeString(buffer, info.id());
            BufferUtil.writeString(buffer, info.version());
            buffer.writeLong(info.timestamp());
            BufferUtil.writeString(buffer, info.name());
            BufferUtil.writeString(buffer, info.desc());
        }
    }

    public record info(String id, String version, long timestamp, String name, String desc) {
    }
}
