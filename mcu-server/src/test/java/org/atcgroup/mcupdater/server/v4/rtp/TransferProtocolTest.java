package org.atcgroup.mcupdater.server.v4.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import me.gb2022.simpnet.channel.FrameDecoder;
import me.gb2022.simpnet.channel.FrameEncoder;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketCodec;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Consumer;

import static org.atcgroup.mcupdater.server.v4.rtp.TransferTestSupport.MTU;
import static org.junit.jupiter.api.Assertions.*;

class TransferProtocolTest {
    @Test
    void registryRoundTripsAllPacketTypes() {
        assertEquals(java.util.List.of("a".repeat(40), "b".repeat(40)), roundTrip(new P62_RTPBatchRequest("batch",
                java.util.List.of("a".repeat(40), "b".repeat(40)))).getResourceIds());
        var offered = new P63_RTPBatchResponse.File("a".repeat(40), "task-a", 123, "a".repeat(40));
        var manifest = roundTrip(new P63_RTPBatchResponse("batch", java.util.List.of(offered),
                java.util.List.of("b".repeat(40)), false));
        assertEquals(java.util.List.of(offered), manifest.getFiles());
        assertEquals(java.util.List.of("b".repeat(40)), manifest.getMissingIds());
        assertFalse(manifest.isRejected());
        assertEquals("task-a", roundTrip(new P71_FTPInlineRequest("task-a")).getTaskId());
        assertEquals("batch", roundTrip(new P64_RTPBatchRelease("batch")).getBatchId());
        var request = roundTrip(new P70_FTPRangeRequest("文件-task", 123));
        assertEquals("文件-task", request.getTaskId());
        assertEquals(123, request.getPartId());

        byte[] data = new byte[MTU];
        new java.util.Random(123).nextBytes(data);
        var part = roundTrip(new P72_FTPPayload("payload", 7, data));
        assertEquals("payload", part.getTaskId());
        assertEquals(7, part.getPartId());
        assertArrayEquals(data, part.getData());

        assertEquals("cancel", roundTrip(new P73_FTPCancel("cancel")).getTaskId());
        assertEquals("query", roundTrip(new P74_FTPStatusRequest("query")).getTaskId());
        for (var status : P75_FTPStatusResult.Status.values()) {
            var result = roundTrip(new P75_FTPStatusResult("result", status));
            assertEquals("result", result.getTaskId());
            assertEquals(status, result.getStatus());
        }
    }

    @Test
    void requiresLowercaseSha1AndMatchingCasIndex() {
        for (var invalid : java.util.List.of("a".repeat(64), "A".repeat(40), "not-a-hash")) {
            assertThrows(IllegalArgumentException.class, () ->
                    new P62_RTPBatchRequest("batch", java.util.List.of(invalid)));
        }
        assertThrows(IllegalArgumentException.class, () -> new P63_RTPBatchResponse.File(
                "a".repeat(40), "task", 1, "b".repeat(40)));
    }

    @Test
    void partWireFormatUsesAnIntIdInsteadOfAnArbitraryOffset() {
        var buffer = Unpooled.buffer();
        try {
            RTPProtocol.PACKETS.encode(new P72_FTPPayload("id", 3, new byte[]{42}), buffer);
            assertEquals(0x72, buffer.readUnsignedByte());
            assertEquals("id", BufferUtil.readString(buffer));
            assertEquals(3, buffer.readInt());
            assertEquals(1, buffer.readInt());
            assertEquals(42, buffer.readUnsignedByte());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 513, Integer.MAX_VALUE})
    void rejectsMalformedTaskIdLengthsBeforeAllocation(int length) {
        reject(buffer -> buffer.writeByte(0x72).writeInt(length));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65537, Integer.MAX_VALUE})
    void rejectsMalformedPayloadLengths(int length) {
        reject(buffer -> {
            buffer.writeByte(0x72);
            BufferUtil.writeString(buffer, "task");
            buffer.writeInt(0).writeInt(length);
        });
    }

    @Test
    void rejectsTruncatedPayloadNegativePartAndUnknownStatus() {
        reject(buffer -> {
            buffer.writeByte(0x72);
            BufferUtil.writeString(buffer, "task");
            buffer.writeInt(0).writeInt(2).writeByte(1);
        });
        reject(buffer -> {
            buffer.writeByte(0x70);
            BufferUtil.writeString(buffer, "task");
            buffer.writeInt(-1);
        });
        reject(buffer -> {
            buffer.writeByte(0x75);
            BufferUtil.writeString(buffer, "task");
            buffer.writeByte(255);
        });
    }

    @Test
    void rejectsTrailingBytesAndUnknownPacketId() {
        reject(buffer -> {
            buffer.writeByte(0x74);
            BufferUtil.writeString(buffer, "task");
            buffer.writeByte(1);
        });
        reject(buffer -> buffer.writeByte(0x7f));
    }

    @Test
    void frameCodecHandlesFragmentedAndCoalescedTcpData() {
        var encoder = new EmbeddedChannel(new FrameEncoder(RTPProtocol.MAX_FRAME_SIZE),
                new PacketCodec(RTPProtocol.PACKETS));
        var decoder = new EmbeddedChannel(new FrameDecoder(RTPProtocol.MAX_FRAME_SIZE),
                new PacketCodec(RTPProtocol.PACKETS));
        var wire = Unpooled.buffer();
        try {
            encoder.writeOutbound(new P70_FTPRangeRequest("task", 0));
            encoder.writeOutbound(new P70_FTPRangeRequest("task", 1));
            ByteBuf piece;
            while ((piece = encoder.readOutbound()) != null) {
                try {
                    wire.writeBytes(piece);
                } finally {
                    piece.release();
                }
            }
            decoder.writeInbound(wire.readRetainedSlice(3));
            assertNull(decoder.readInbound(), "Partial frame header must not decode");
            decoder.writeInbound(wire.readRetainedSlice(2));
            assertNull(decoder.readInbound(), "Partial frame payload must not decode");
            decoder.writeInbound(wire.readRetainedSlice(wire.readableBytes()));
            assertEquals(0, decoder.<P70_FTPRangeRequest>readInbound().getPartId());
            assertEquals(1, decoder.<P70_FTPRangeRequest>readInbound().getPartId());
            assertNull(decoder.readInbound());
        } finally {
            wire.release();
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void constructorsRejectOversizedPayloadAndInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new P72_FTPPayload("task", 0, new byte[MTU + 1]));
        assertThrows(IllegalArgumentException.class, () -> new P72_FTPPayload("task", -1, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new P70_FTPRangeRequest("", 0));
        assertThrows(IllegalArgumentException.class, () -> new P70_FTPRangeRequest("task", -1));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Packet> T roundTrip(T packet) {
        var buffer = Unpooled.buffer();
        try {
            RTPProtocol.PACKETS.encode(packet, buffer);
            var decoded = RTPProtocol.PACKETS.decode(buffer);
            assertEquals(packet.getClass(), decoded.getClass());
            assertFalse(buffer.isReadable());
            return (T) decoded;
        } finally {
            buffer.release();
        }
    }

    private static void reject(Consumer<ByteBuf> populate) {
        var buffer = Unpooled.buffer();
        try {
            populate.accept(buffer);
            assertThrows(RuntimeException.class, () -> RTPProtocol.PACKETS.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
