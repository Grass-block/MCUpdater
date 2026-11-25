package me.gb2022.mcupdater.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.mcupdater.rtp.TransferFileReceiver;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Complete partition of the requested IDs, sent before any file payload. */
public final class P63_RTPBatchResponse implements Packet {
    public record File(String resourceId, String taskId, long size, String sha1) {
        public File {
            RTPProtocol.validateSha1(resourceId);
            RTPProtocol.validateSha1(sha1);
            if (!resourceId.equals(sha1)) throw new IllegalArgumentException("CAS index and digest differ");
            RTPProtocol.validateTaskId(taskId);
            if (size < 0 || size > (long) TransferFileReceiver.FILE_MTU * TransferFileReceiver.MAX_PART_COUNT
                    || sha1 == null || !sha1.matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("Invalid file metadata");
            }
        }
    }

    private final String batchId;
    private final List<File> files;
    private final List<String> missingIds;
    private final boolean rejected;

    public P63_RTPBatchResponse(String batchId, List<File> files, List<String> missingIds, boolean rejected) {
        RTPProtocol.validateTaskId(batchId);
        if (files.size() + missingIds.size() > P62_RTPBatchRequest.MAX_FILES
                || (rejected && (!files.isEmpty() || !missingIds.isEmpty()))) {
            throw new IllegalArgumentException("Invalid manifest");
        }
        var ids = new HashSet<String>();
        var tasks = new HashSet<String>();
        for (var file : files) {
            if (!ids.add(file.resourceId()) || !tasks.add(file.taskId())) throw new IllegalArgumentException("Duplicate file");
        }
        for (var id : missingIds) {
            RTPProtocol.validateSha1(id);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate missing ID");
        }
        this.batchId = batchId;
        this.files = List.copyOf(files);
        this.missingIds = List.copyOf(missingIds);
        this.rejected = rejected;
    }

    @DeserializedConstructor
    public P63_RTPBatchResponse(ByteBuf buffer) {
        this(RTPProtocol.readTaskId(buffer), readFiles(buffer), P62_RTPBatchRequest.readIds(buffer), buffer.readBoolean());
        RTPProtocol.requireEnd(buffer);
    }

    private static List<File> readFiles(ByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > P62_RTPBatchRequest.MAX_FILES) throw new IllegalArgumentException("Invalid file count");
        var files = new ArrayList<File>(count);
        for (int i = 0; i < count; i++) {
            files.add(new File(RTPProtocol.readTaskId(buffer), RTPProtocol.readTaskId(buffer),
                    buffer.readLong(), RTPProtocol.readTaskId(buffer)));
        }
        return files;
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.batchId);
        buffer.writeInt(this.files.size());
        for (var file : this.files) {
            BufferUtil.writeString(buffer, file.resourceId());
            BufferUtil.writeString(buffer, file.taskId());
            buffer.writeLong(file.size());
            BufferUtil.writeString(buffer, file.sha1());
        }
        buffer.writeInt(this.missingIds.size());
        this.missingIds.forEach(id -> BufferUtil.writeString(buffer, id));
        buffer.writeBoolean(this.rejected);
    }

    public String getBatchId() { return this.batchId; }
    public List<File> getFiles() { return this.files; }
    public List<String> getMissingIds() { return this.missingIds; }
    public boolean isRejected() { return this.rejected; }
}
