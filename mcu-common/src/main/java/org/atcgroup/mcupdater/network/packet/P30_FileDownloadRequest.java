package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.data.DownloadFileList;

import java.util.Map;

public final class P30_FileDownloadRequest implements Packet {
    private final DownloadFileList files;

    public P30_FileDownloadRequest(DownloadFileList files) {
        this.files = files;
    }

    @DeserializedConstructor
    public P30_FileDownloadRequest(ByteBuf buffer) {
        this.files = new DownloadFileList(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        this.files.serialize(buffer);
    }

    public DownloadFileList getFiles() {
        return files;
    }
}
