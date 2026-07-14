package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.data.DownloadFileList;

import java.awt.*;

public final class P31_FileDownloadStart implements Packet {
    private final String taskId;
    private final DownloadFileList failedFiles;

    public P31_FileDownloadStart(String taskId, DownloadFileList failedFiles) {
        this.taskId = taskId;
        this.failedFiles = failedFiles;
    }

    @DeserializedConstructor
    public P31_FileDownloadStart(ByteBuf buffer) {
        this.taskId = BufferUtil.readString(buffer);
        this.failedFiles = new DownloadFileList(buffer);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.taskId);
        this.failedFiles.serialize(buffer);
    }

    public String getTaskId() {
        return taskId;
    }

    public DownloadFileList getFailedFiles() {
        return failedFiles;
    }
}
