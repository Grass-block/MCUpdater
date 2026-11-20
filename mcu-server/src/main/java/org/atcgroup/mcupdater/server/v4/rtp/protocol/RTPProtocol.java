package org.atcgroup.mcupdater.server.v4.rtp.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.PacketRegistry;
import me.gb2022.simpnet.util.BufferUtil;

public interface RTPProtocol {
    PacketRegistry PACKETS = new PacketRegistry(128, RTPProtocol::init);
    int MAX_FRAME_SIZE = P72_FTPPayload.MAX_PAYLOAD_SIZE + 1024;

    static void init(PacketRegistry registry) {
        //0x60 Resource-Transfer protocol
        registry.register(0x60, P60_RTPProtocolInit.class);
        registry.register(0x61, P61_RTPProtocolInfo.class);
        registry.register(0x62, P62_RTPBatchRequest.class);
        registry.register(0x63, P63_RTPBatchResponse.class);
        registry.register(0x64, P64_RTPBatchRelease.class);

        //0x70 B-FTP protocol
        registry.register(0x70, P70_FTPRangeRequest.class);
        registry.register(0x71, P71_FTPInlineRequest.class);
        registry.register(0x72, P72_FTPPayload.class);
        registry.register(0x73, P73_FTPCancel.class);
        registry.register(0x74, P74_FTPStatusRequest.class);
        registry.register(0x75, P75_FTPStatusResult.class);
    }

    static void validateSha1(String sha1) {
        if (sha1 == null || !sha1.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-1 digest");
        }
    }

    static void validateTaskId(String taskId) {
        if (taskId == null || taskId.isEmpty() || taskId.length() > 128) {
            throw new IllegalArgumentException("Invalid task id");
        }
    }

    static void requireEnd(ByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Unexpected packet payload");
        }
    }

    static String readTaskId(ByteBuf buffer) {
        if (buffer.readableBytes() < Integer.BYTES) {
            throw new IllegalArgumentException("Missing task id length");
        }
        int length = buffer.getInt(buffer.readerIndex());
        if (length <= 0 || length > 512 || length > buffer.readableBytes() - Integer.BYTES) {
            throw new IllegalArgumentException("Invalid task id length");
        }
        return BufferUtil.readString(buffer);
    }
}
