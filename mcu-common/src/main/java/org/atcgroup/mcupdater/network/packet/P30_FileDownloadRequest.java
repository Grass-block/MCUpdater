package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P30_FileDownloadRequest implements Packet {
    private final Map<String, String> files;     //res-id:sha256

    public P30_FileDownloadRequest(Map<String, String> files) {
        this.files = files;
    }

    @DeserializedConstructor
    public P30_FileDownloadRequest(ByteBuf buffer) {
        this.files = new HashMap<>();

        var len = buffer.readInt();
        for (int i = 0; i < len; i++) {
            this.files.put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.files.size());
        for (Map.Entry<String, String> entry : this.files.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            BufferUtil.writeString(buffer, entry.getValue());
        }
    }

    public Map<String, String> getFiles() {
        return files;
    }
}
