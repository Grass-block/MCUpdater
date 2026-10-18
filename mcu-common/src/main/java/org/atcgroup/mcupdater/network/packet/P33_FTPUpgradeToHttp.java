package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P33_FTPUpgradeToHttp implements Packet {
    private final int remoteServicePort;
    private final String remoteServerRepo;
    private final Map<String, String> fileToDownload;

    public P33_FTPUpgradeToHttp(int remoteServicePort, String remoteServerRepo, Map<String, String> fileToDownload) {
        this.remoteServicePort = remoteServicePort;
        this.remoteServerRepo = remoteServerRepo;
        this.fileToDownload = fileToDownload;
    }

    @DeserializedConstructor
    public P33_FTPUpgradeToHttp(ByteBuf buffer) {
        this.remoteServicePort = buffer.readInt();
        this.remoteServerRepo = BufferUtil.readString(buffer);
        this.fileToDownload = new HashMap<>();
        var len = buffer.readInt();
        for (var i = 0; i < len; i++) {
            this.fileToDownload.put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.remoteServicePort);
        BufferUtil.writeString(buffer, this.remoteServerRepo);
        buffer.writeInt(this.fileToDownload.size());
        for (var e : this.fileToDownload.entrySet()) {
            BufferUtil.writeString(buffer, e.getKey());
            BufferUtil.writeString(buffer, e.getValue());
        }
    }

    public int getRemoteServicePort() {
        return remoteServicePort;
    }

    public String getRemoteServerRepo() {
        return remoteServerRepo;
    }

    public Map<String, String> getFileToDownload() {
        return fileToDownload;
    }
}
