package me.gb2022.mcupdater.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import me.gb2022.mcupdater.rtp.protocol.*;
import me.gb2022.simpnet.channel.FrameDecoder;
import me.gb2022.simpnet.channel.FrameEncoder;
import me.gb2022.simpnet.packet.PacketCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Standalone integration checks; no test-framework dependency is required. */
public final class TransferReceiveTest {
    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("mcu-transfer-test-");
        try {
            rangedAndDisconnect(root);
            inlineAndHashFailure(root);
            cancellationAndTimeout(root);
            boundedExecutor(root);
            malformedPacket();
            rangeRequestsAndResults(root);
            System.out.println("TransferReceiveTest: all checks passed");
        } finally {
            try (var files = Files.walk(root)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void rangedAndDisconnect(Path root) throws Exception {
        var manager = new TransferManager(4, 64, 10);
        var first = channel(manager);
        var second = channel(manager);
        try {
            byte[] data = new byte[65536 * 8 + 17];
            new java.util.Random(42).nextBytes(data);
            var destination = root.resolve("ranged.bin");
            Files.writeString(destination, "old file");
            var task = manager.register("ranged", destination, data.length, hash(data), TransferMode.RANGED,
                    Duration.ofSeconds(10));
            check(task.nextMissingPart(new java.util.BitSet()) == 0, "Part planning");
            send(first, new P72_FTPPayload("ranged", 0, Arrays.copyOfRange(data, 0, 65536)));
            first.finishAndReleaseAll();
            check(manager.getTask("ranged") == task, "Disconnect must preserve task");
            check(Files.readString(destination).equals("old file"), "Destination changed before verification");
            for (int index = 8; index > 0; index--) {
                int start = index * 65536;
                send(second, new P72_FTPPayload("ranged", index,
                        Arrays.copyOfRange(data, start, Math.min(data.length, start + 65536))));
            }
            await(task.completion());
            check(Arrays.equals(data, Files.readAllBytes(destination)), "Concurrent ranges corrupted");
            check(task.getReceivedPartsSnapshot().cardinality() == 9, "Received bit count");
            check(task.getState() == TransferTask.State.COMPLETED, "Completion state");
            check(task.nextMissingPart(new java.util.BitSet()) == -1, "Completed part planning");
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
            manager.close();
            await(manager.termination());
        }
    }

    private static void inlineAndHashFailure(Path root) throws Exception {
        var manager = new TransferManager();
        try {
            for (byte[] data : new byte[][]{new byte[0], "inline content".getBytes(java.nio.charset.StandardCharsets.UTF_8)}) {
                String id = "inline-" + data.length;
                var task = manager.register(id, root.resolve(id), data.length, hash(data), TransferMode.INLINE,
                        Duration.ofSeconds(10));
                check(task.getReceivedPartsSnapshot() == null && task.nextMissingPart(new java.util.BitSet()) == -1, "INLINE allocated ranges");
                await(manager.receive(new P72_FTPPayload(id, 0, data)));
                await(task.completion());
                check(Arrays.equals(data, Files.readAllBytes(task.getFile())), "INLINE content");
            }
            var destination = root.resolve("bad-hash");
            Files.writeString(destination, "preserved");
            var task = manager.register("bad", destination, 3, hash(new byte[3]), TransferMode.INLINE,
                    Duration.ofSeconds(10));
            expectFailure(manager.receive(new P72_FTPPayload("bad", 0, new byte[]{1, 2, 3})));
            expectFailure(task.completion());
            check(task.getState() == TransferTask.State.FAILED, "Hash failure state");
            check(Files.readString(destination).equals("preserved"), "Hash failure replaced destination");
        } finally {
            manager.close();
            await(manager.termination());
        }
        noStaging(root);
    }

    private static void cancellationAndTimeout(Path root) throws Exception {
        var manager = new TransferManager();
        try {
            var task = manager.register("cancel", root.resolve("cancel"), 65537, hash(new byte[65537]),
                    TransferMode.RANGED, Duration.ofSeconds(10));
            expectFailure(manager.receive(new P72_FTPPayload("cancel", 1, new byte[65536])));
            check(task.getReceivedParts() == 0, "Invalid part changed BitSet");
            await(manager.receive(new P72_FTPPayload("cancel", 0, new byte[65536])));
            await(manager.receive(new P72_FTPPayload("cancel", 0, new byte[65536])));
            check(task.getReceivedParts() == 1, "Duplicate counted twice");
            check(manager.cancel("cancel"), "Cancellation rejected");
            expectFailure(task.completion());
            check(task.getState() == TransferTask.State.CANCELLED, "Cancellation state");
            var timeout = manager.register("timeout", root.resolve("timeout"), 65537, hash(new byte[65537]),
                    TransferMode.RANGED, Duration.ofMillis(50));
            await(manager.receive(new P72_FTPPayload("timeout", 0, new byte[65536])));
            expectFailure(timeout.completion());
            check(timeout.getState() == TransferTask.State.TIMED_OUT, "Timeout state");
            var closing = manager.register("close", root.resolve("close"), 1, hash(new byte[1]),
                    TransferMode.INLINE, Duration.ofSeconds(10));
            manager.close();
            expectFailure(closing.completion());
        } finally {
            manager.close();
            await(manager.termination());
        }
        noStaging(root);
    }

    private static void boundedExecutor(Path root) throws Exception {
        var receiver = new TransferFileReceiver("rejected", root.resolve("rejected"), 1, hash(new byte[1]),
                TransferMode.INLINE, Duration.ofSeconds(10));
        expectFailure(receiver.receive(0, new byte[1], runnable -> {
            throw new java.util.concurrent.RejectedExecutionException("full");
        }));
        check(receiver.getReceivedParts() == 0, "Rejected write marked received");
        check(receiver.getState() == TransferTask.State.RECEIVING, "Rejected write destroyed task");
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            await(receiver.receive(0, new byte[1], workers));
            await(receiver.completion());
        } finally {
            workers.shutdown();
            check(workers.awaitTermination(10, TimeUnit.SECONDS), "Worker did not stop");
        }
    }

    private static EmbeddedChannel channel(TransferManager manager) {
        return new EmbeddedChannel(new FrameEncoder(RTPProtocol.MAX_FRAME_SIZE),
                new FrameDecoder(RTPProtocol.MAX_FRAME_SIZE), new PacketCodec(RTPProtocol.PACKETS),
                new FileReceiver(manager));
    }

    private static void malformedPacket() {
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            buffer.writeByte(0x71).writeInt(Integer.MAX_VALUE);
            try {
                RTPProtocol.PACKETS.decode(buffer);
                throw new AssertionError("Unbounded task id accepted");
            } catch (me.gb2022.simpnet.packet.InvalidPacketFormatException expected) {
                // Reject invalid lengths before BufferUtil allocates an array.
            }
        } finally {
            buffer.release();
        }
    }

    private static void send(EmbeddedChannel receiver, me.gb2022.simpnet.packet.Packet part) {
        var encoder = new EmbeddedChannel(new FrameEncoder(RTPProtocol.MAX_FRAME_SIZE),
                new PacketCodec(RTPProtocol.PACKETS));
        try {
            encoder.writeOutbound(part);
            ByteBuf buffer;
            while ((buffer = encoder.readOutbound()) != null) {
                receiver.writeInbound(buffer);
            }
        } finally {
            encoder.finishAndReleaseAll();
        }
    }

    private static void rangeRequestsAndResults(Path root) throws Exception {
        var manager = new TransferManager();
        var first = channel(manager);
        var second = channel(manager);
        var reconnect = channel(manager);
        try {
            byte[] data = new byte[65536 + 2];
            var task = manager.register("wire", root.resolve("wire"), data.length, hash(data),
                    TransferMode.RANGED, Duration.ofSeconds(10));
            enlist(manager, first, "wire", 1);
            var ranges = (P70_FTPRangeRequest) awaitPacket(first);
            check(ranges.getPartId() == 0, "First requested part id");
            enlist(manager, second, "wire", 1);
            var secondRanges = (P70_FTPRangeRequest) awaitPacket(second);
            check(secondRanges.getPartId() == 1,
                    "Concurrent Channels requested the same range");
            send(second, new P74_FTPStatusRequest("wire"));
            check(((P75_FTPStatusResult) awaitPacket(second)).getStatus() == P75_FTPStatusResult.Status.RECEIVING,
                    "Active status response");
            send(first, new P72_FTPPayload("wire", 0, new byte[65536]));
            // No terminal status may be emitted for a partially received file.
            check(first.readOutbound() == null, "Premature completion report");
            send(second, new P72_FTPPayload("wire", 1, new byte[2]));
            await(task.completion());
            check(((P75_FTPStatusResult) awaitPacket(first)).getStatus() == P75_FTPStatusResult.Status.COMPLETED,
                    "First Channel did not receive completion");
            check(((P75_FTPStatusResult) awaitPacket(second)).getStatus() == P75_FTPStatusResult.Status.COMPLETED,
                    "Second Channel did not receive completion");
            send(reconnect, new P74_FTPStatusRequest("wire"));
            check(((P75_FTPStatusResult) awaitPacket(reconnect)).getStatus() == P75_FTPStatusResult.Status.COMPLETED,
                    "Reconnect lost terminal result");
            send(reconnect, new P74_FTPStatusRequest("unknown"));
            check(((P75_FTPStatusResult) awaitPacket(reconnect)).getStatus() == P75_FTPStatusResult.Status.UNKNOWN,
                    "Unknown range request not reported");

            var bad = manager.register("wire-bad", root.resolve("wire-bad"), 1, hash(new byte[]{0}),
                    TransferMode.INLINE, Duration.ofSeconds(10));
            var invalidScheduling = manager.requestRanges("wire-bad", reconnect, 1);
            reconnect.runPendingTasks();
            expectFailure(invalidScheduling);
            check(reconnect.readOutbound() == null, "INLINE triggered range scheduling");
            send(reconnect, new P72_FTPPayload("wire-bad", 0, new byte[]{1}));
            expectFailure(bad.completion());
            check(((P75_FTPStatusResult) awaitPacket(reconnect)).getStatus() == P75_FTPStatusResult.Status.FAILED,
                    "Hash failure not reported on wire");

            var cancel = manager.register("wire-cancel", root.resolve("wire-cancel"), 1, hash(new byte[1]),
                    TransferMode.INLINE, Duration.ofSeconds(10));
            send(reconnect, new P73_FTPCancel("wire-cancel"));
            expectFailure(cancel.completion());
            check(((P75_FTPStatusResult) awaitPacket(reconnect)).getStatus() == P75_FTPStatusResult.Status.CANCELLED,
                    "Cancellation not reported on wire");

            var timeout = manager.register("wire-timeout", root.resolve("wire-timeout"), 1, hash(new byte[1]),
                    TransferMode.INLINE, Duration.ofMillis(50));
            send(reconnect, new P74_FTPStatusRequest("wire-timeout"));
            check(((P75_FTPStatusResult) awaitPacket(reconnect)).getStatus() == P75_FTPStatusResult.Status.RECEIVING,
                    "Timeout subscription status");
            expectFailure(timeout.completion());
            check(((P75_FTPStatusResult) awaitPacket(reconnect)).getStatus() == P75_FTPStatusResult.Status.TIMED_OUT,
                    "Timeout not reported on wire");

            var rescheduled = manager.register("reschedule", root.resolve("reschedule"), 65536 * 2 + 2, hash(new byte[65536 * 2 + 2]),
                    TransferMode.RANGED, Duration.ofSeconds(10));
            enlist(manager, first, "reschedule", 1);
            check(((P70_FTPRangeRequest) awaitPacket(first)).getPartId() == 0,
                    "First request offset");
            enlist(manager, second, "reschedule", 1);
            check(((P70_FTPRangeRequest) awaitPacket(second)).getPartId() == 1,
                    "Second request offset");
            first.finishAndReleaseAll();
            send(second, new P72_FTPPayload("reschedule", 1, new byte[65536]));
            check(((P70_FTPRangeRequest) awaitPacket(second)).getPartId() == 0,
                    "Disconnected Channel range was not reassigned");
            send(second, new P72_FTPPayload("reschedule", 0, new byte[65536]));
            check(((P70_FTPRangeRequest) awaitPacket(second)).getPartId() == 2,
                    "Window did not refill after successful write");
            send(second, new P72_FTPPayload("reschedule", 2, new byte[2]));
            await(rescheduled.completion());
            check(((P75_FTPStatusResult) awaitPacket(second)).getStatus() == P75_FTPStatusResult.Status.COMPLETED,
                    "Rescheduled task did not finish");
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
            reconnect.finishAndReleaseAll();
            manager.close();
            await(manager.termination());
        }
    }

    private static void enlist(TransferManager manager, EmbeddedChannel channel, String taskId, int window)
            throws Exception {
        var future = manager.requestRanges(taskId, channel, window);
        channel.runPendingTasks();
        await(future);
    }

    private static me.gb2022.simpnet.packet.Packet awaitPacket(EmbeddedChannel channel) throws Exception {
        var decoder = new EmbeddedChannel(new FrameDecoder(RTPProtocol.MAX_FRAME_SIZE),
                new PacketCodec(RTPProtocol.PACKETS));
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                channel.runPendingTasks();
                ByteBuf buffer;
                while ((buffer = channel.readOutbound()) != null) {
                    decoder.writeInbound(buffer);
                    var packet = decoder.<me.gb2022.simpnet.packet.Packet>readInbound();
                    if (packet != null) {
                        return packet;
                    }
                }
                Thread.yield();
            }
            throw new AssertionError("No response packet");
        } finally {
            decoder.finishAndReleaseAll();
        }
    }

    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static void expectFailure(CompletionStage<?> stage) throws Exception {
        try {
            await(stage);
            throw new AssertionError("Expected failure");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException expected) {
            // A timeout in this test is a failure, not an expected task error.
        }
    }

    private static void noStaging(Path root) throws Exception {
        try (var files = Files.list(root)) {
            check(files.noneMatch(path -> path.toString().endsWith(".part")), "Staging file leaked");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

