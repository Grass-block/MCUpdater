package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.CompiledVersionData;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.data.HttpDownloadInfo;

public final class P21_VersionHeaders implements Packet {
    private final UpdateHeader header;

    public P21_VersionHeaders(UpdateHeader header) {
        this.header = header;
    }

    @DeserializedConstructor
    public P21_VersionHeaders(ByteBuf buffer) {
        this.header = new UpdateHeader();

        var versionLen = buffer.readInt();
        for (var i = 0; i < versionLen; i++) {
            var channel = BufferUtil.readString(buffer);
            var channelDisplayName = BufferUtil.readString(buffer);
            var headTimeStamp = buffer.readLong();
            var data = new CompiledVersionData(channel, channelDisplayName, headTimeStamp);

            var logLen = buffer.readInt();
            for (var j = 0; j < logLen; j++) {
                data.addElapsedUpdateLog(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
            }

            this.header.addVersionData(data);
        }

        var serverDownloadLen = buffer.readInt();
        for (var i = 0; i < serverDownloadLen; i++) {
            this.header.getServerDownloads().put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        }

        var externDownloadLen = buffer.readInt();
        for (var i = 0; i < externDownloadLen; i++) {
            var url = BufferUtil.readString(buffer);
            var dest = BufferUtil.readString(buffer);
            var size = buffer.readLong();
            var sha1 = BufferUtil.readString(buffer);

            this.header.getExternDownloads().add(HttpDownloadInfo.of(url, dest, size, sha1));
        }

        var replaceFileLen = buffer.readInt();
        for (var i = 0; i < replaceFileLen; i++) {
            this.header.getReplaceFiles().put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
        }

        var expandFileLen = buffer.readInt();
        for (var i = 0; i < expandFileLen; i++) {
            this.header.getExpandFiles().add(BufferUtil.readString(buffer));
        }

        var deleteFileLen = buffer.readInt();
        for (var i = 0; i < deleteFileLen; i++) {
            this.header.getDeleteFiles().add(BufferUtil.readString(buffer));
        }
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.header.getVersions().size());
        for (var data : this.header.getVersions()) {
            BufferUtil.writeString(buffer, data.getChannel());
            BufferUtil.writeString(buffer, data.getChannelDisplayName());
            buffer.writeLong(data.getHeadTimeStamp());

            var logs = data.getElapsedUpdateLogs();
            buffer.writeInt(logs.size());
            for (var e : logs.entrySet()) {
                BufferUtil.writeString(buffer, e.getKey());
                BufferUtil.writeString(buffer, e.getValue());
            }
        }

        buffer.writeInt(this.header.getServerDownloads().size());
        for (var e : this.header.getServerDownloads().entrySet()) {
            BufferUtil.writeString(buffer, e.getKey());
            BufferUtil.writeString(buffer, e.getValue());
        }

        buffer.writeInt(this.header.getExternDownloads().size());
        for (var e : this.header.getExternDownloads()) {
            BufferUtil.writeString(buffer, e.getUrl());
            BufferUtil.writeString(buffer, e.getDest());
            buffer.writeLong(e.getSize());
            BufferUtil.writeString(buffer, e.getSha1());
        }

        buffer.writeInt(this.header.getReplaceFiles().size());
        for (var e : this.header.getReplaceFiles().entrySet()) {
            BufferUtil.writeString(buffer, e.getKey());
            BufferUtil.writeString(buffer, e.getValue());
        }

        buffer.writeInt(this.header.getExpandFiles().size());
        for (var f : this.header.getExpandFiles()) {
            BufferUtil.writeString(buffer, f);
        }

        buffer.writeInt(this.header.getDeleteFiles().size());
        for (var f : this.header.getDeleteFiles()) {
            BufferUtil.writeString(buffer, f);
        }
    }

    public UpdateHeader getHeader() {
        return header;
    }
}
