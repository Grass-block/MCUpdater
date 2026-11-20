package org.atcgroup.mcupdater.server.v4.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.server.v4.rtp.TransferFileReceiver;

public final class P72_FTPPayload implements Packet {
    public static final int MAX_PAYLOAD_SIZE = TransferFileReceiver.FILE_MTU;
    private final String filePointer;
    private final int partId;
    private final byte[] data;

    public P72_FTPPayload(String taskId, int partId, byte[] data) {
        if (taskId == null || taskId.isEmpty() || taskId.length() > 128 || partId < 0
                || data == null || data.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Invalid file part");
        }
        this.filePointer = taskId;
        this.partId = partId;
        this.data = data;
    }

    @DeserializedConstructor
    public P72_FTPPayload(ByteBuf buffer) {
        this.filePointer = RTPProtocol.readTaskId(buffer);
        this.partId = buffer.readInt();
        int length = buffer.readInt();
        if (this.filePointer.isEmpty() || this.filePointer.length() > 128 || this.partId < 0
                || length < 0 || length > MAX_PAYLOAD_SIZE || length != buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid file part");
        }
        this.data = new byte[length];
        buffer.readBytes(this.data);
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.filePointer);
        buffer.writeInt(this.partId);
        buffer.writeInt(this.data.length);
        buffer.writeBytes(this.data);
    }

    public String getFilePointer() {
        return filePointer;
    }

    public String getTaskId() {
        return this.filePointer;
    }

    public byte[] getData() {
        return data;
    }

    public int getPartId() {
        return this.partId;
    }

    @Override
    public String toString() {
        return "P71_PacketFilePart{" + "partId=" + this.partId + ", filePointer='" + filePointer + '\'' + '}';
    }
}
