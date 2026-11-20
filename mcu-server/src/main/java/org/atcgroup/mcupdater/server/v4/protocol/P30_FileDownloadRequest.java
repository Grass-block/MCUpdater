package org.atcgroup.mcupdater.server.v4.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashSet;
import java.util.Set;

public final class P30_FileDownloadRequest implements Packet {
    private final Set<String> files;     //res-id:sha256

    public P30_FileDownloadRequest(Set<String> files) {
        this.files = files;
    }

    @DeserializedConstructor
    public P30_FileDownloadRequest(ByteBuf buffer) {
        this.files = new HashSet<>();

        var len = buffer.readInt();
        for (int i = 0; i < len; i++) {
            this.files.add(BufferUtil.readString(buffer));
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.files.size());
        for (var sha1 : this.files) {
            BufferUtil.writeString(buffer, sha1);
        }
    }

    public Set<String> getFiles() {
        return files;
    }
}