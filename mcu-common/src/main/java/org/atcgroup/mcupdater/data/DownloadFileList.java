package org.atcgroup.mcupdater.data;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.util.FileChecksumManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DownloadFileList extends HashMap<String, Map<String, byte[]>> {
    public DownloadFileList(ByteBuf buffer) {
        var length = buffer.readInt();

        for (var i = 0; i < length; i++) {
            var channel = BufferUtil.readString(buffer);
            var len = buffer.readInt();
            var files = new HashMap<String, byte[]>();

            for (var j = 0; j < len; j++) {
                files.put(BufferUtil.readString(buffer), BufferUtil.readArray(buffer));
            }

            this.put(channel, files);
        }
    }

    public DownloadFileList() {
        super();
    }

    public DownloadFileList(Set<VersionInfo> versions, FileChecksumManager checksumManager) {
        for (var v : versions) {
            var channel = v.getChannel();
            var files = this.computeIfAbsent(channel, k -> new HashMap<>());

            for (var d : v.getResourcePackList()) {
                var sum = checksumManager.getFileChecksum(d);
                files.put(d, sum);
            }

            for (var d : v.getUpdateFileList().values()) {
                files.put(d, checksumManager.getFileChecksum(d));
            }
        }
    }

    public void addFile(String channel, String file, byte[] sum) {
        this.computeIfAbsent(channel, k -> new HashMap<>()).put(file, sum);
    }

    public void serialize(ByteBuf buffer) {
        buffer.writeInt(this.size());

        for (var entry : this.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            buffer.writeInt(entry.getValue().size());

            for (var e : entry.getValue().entrySet()) {
                BufferUtil.writeString(buffer, e.getKey());
                BufferUtil.writeArray(buffer, e.getValue());
            }
        }
    }
}
