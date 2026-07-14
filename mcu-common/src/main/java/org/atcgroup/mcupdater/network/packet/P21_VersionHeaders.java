package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.util.FileChecksumManager;
import org.atcgroup.mcupdater.data.DownloadFileList;
import org.atcgroup.mcupdater.data.VersionInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class P21_VersionHeaders implements Packet {
    private final Set<VersionInfo> versions;
    private final DownloadFileList fileList;

    public P21_VersionHeaders(Set<VersionInfo> versions, FileChecksumManager checksumManager) {
        this.versions = versions;
        this.fileList = new DownloadFileList(versions, checksumManager);
    }

    @DeserializedConstructor
    public P21_VersionHeaders(ByteBuf buffer) {
        this.versions = new HashSet<>();
        this.fileList = new DownloadFileList(buffer);

        var len = buffer.readInt();

        for (var j = 0; j < len; j++) {
            var channel = BufferUtil.readString(buffer);
            var time = buffer.readLong();
            var version = BufferUtil.readString(buffer);
            var downloadPackList = new HashSet<String>();
            var deleteFileList = new HashSet<String>();
            var updateFileList = new HashMap<String, String>();
            var downloadFileList = new HashMap<String, String>();

            var packListLen = buffer.readInt();
            for (var i = 0; i < packListLen; i++) {
                downloadPackList.add(BufferUtil.readString(buffer));
            }

            var deleteFileLen = buffer.readInt();
            for (var i = 0; i < deleteFileLen; i++) {
                deleteFileList.add(BufferUtil.readString(buffer));
            }

            var updateFileLen = buffer.readInt();
            for (var i = 0; i < updateFileLen; i++) {
                updateFileList.put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
            }

            for (var i = 0; i < downloadPackList.size(); i++) {
                downloadFileList.put(BufferUtil.readString(buffer), BufferUtil.readString(buffer));
            }

            var ver = new VersionInfo(channel, time, version, deleteFileList, downloadPackList, updateFileList, downloadFileList);

            this.versions.add(ver);
        }

    }

    @Override
    public void write(ByteBuf buffer) {
        this.fileList.serialize(buffer);
        buffer.writeInt(this.versions.size());

        for (var ver : this.versions) {
            var channel = ver.getChannel();
            var time = ver.getTimestamp();
            var version = ver.getVersion();
            var deleteFileList = ver.getDeleteFileList();
            var downloadPackList = ver.getDownloadPackList();
            var updateFileList = ver.getUpdateFileList();
            var downloadFileList = ver.getDownloadFileList();

            BufferUtil.writeString(buffer, channel);
            buffer.writeLong(time);
            BufferUtil.writeString(buffer, version);

            buffer.writeInt(deleteFileList.size());
            for (var file : deleteFileList) {
                BufferUtil.writeString(buffer, file);
            }

            buffer.writeInt(downloadPackList.size());
            for (var pack : downloadPackList) {
                BufferUtil.writeString(buffer, pack);
            }

            buffer.writeInt(updateFileList.size());
            for (var e : updateFileList.entrySet()) {
                BufferUtil.writeString(buffer, e.getKey());
                BufferUtil.writeString(buffer, e.getValue());
            }

            buffer.writeInt(downloadFileList.size());
            for (var e : downloadFileList.entrySet()) {
                BufferUtil.writeString(buffer, e.getKey());
                BufferUtil.writeString(buffer, e.getValue());
            }
        }
    }

    public Set<VersionInfo> getVersions() {
        return versions;
    }

    public DownloadFileList getFileList() {
        return fileList;
    }
}
