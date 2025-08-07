package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class P12_FileDelete implements Packet {
    private final Set<String> paths = new HashSet<>();

    public P12_FileDelete(String... path) {
        this.paths.addAll(List.of(path));
    }

    @DeserializedConstructor
    public P12_FileDelete(ByteBuf buffer) {
        var len = buffer.readInt();
        for (var i = 0; i < len; i++) {
            this.paths.add(BufferUtil.readString(buffer));
        }
    }

    public Set<String> getPaths() {
        return paths;
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.paths.size());
        for (String path : this.paths) {
            BufferUtil.writeString(buffer, path);
        }
    }
}
