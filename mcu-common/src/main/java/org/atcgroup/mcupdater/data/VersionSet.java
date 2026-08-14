package org.atcgroup.mcupdater.data;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class VersionSet extends HashMap<String, Set<String>> {
    public VersionSet(ByteBuf buffer) {
        var length = buffer.readShort();

        for (var i = 0; i < length; i++) {
            var channel = BufferUtil.readString(buffer);
            var items = buffer.readShort();

            for (var j = 0; j < items; j++) {
                this.computeIfAbsent(channel, k -> new HashSet<>()).add(BufferUtil.readString(buffer));
            }
        }
    }

    public VersionSet() {
        super();
    }

    public void addVersion(String channel, String version) {
        this.computeIfAbsent(channel, k -> new HashSet<>()).add(version);
    }

    public void write(ByteBuf buffer) {
        buffer.writeShort(this.size());

        for (var slot : this.keySet()) {
            BufferUtil.writeString(buffer, slot);
            var versions = this.get(slot);
            buffer.writeShort(versions.size());
            for (var i = 0; i < versions.size(); i++) {
                BufferUtil.writeString(buffer, slot);
            }
        }
    }
}
