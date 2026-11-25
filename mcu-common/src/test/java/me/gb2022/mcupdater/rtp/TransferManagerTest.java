package me.gb2022.mcupdater.rtp;

import me.gb2022.mcupdater.rtp.protocol.P72_FTPPayload;
import me.gb2022.mcupdater.rtp.protocol.P75_FTPStatusResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TransferManagerTest {
    @TempDir
    Path directory;

    private final TransferManager manager = new TransferManager(4, 64, 32);

    @AfterEach
    void closeManager() throws Exception {
        this.manager.close();
        TransferTestSupport.await(this.manager.termination());
        try (var paths = Files.list(this.directory)) {
            assertFalse(paths.anyMatch(path -> path.toString().endsWith(".part")), "Staging file leaked");
        }
    }

    @Test
    void concurrentShuffledPartsProduceExactFileContent() throws Exception {
        byte[] data = new byte[TransferTestSupport.MTU * 24 + 17];
        new Random(12345).nextBytes(data);
        var task = register("concurrent", data, TransferMode.RANGED);
        var ids = new ArrayList<Integer>();
        for (int id = 0; id < 25; id++) {
            ids.add(id);
        }
        Collections.shuffle(ids, new Random(42));
        var senders = Executors.newFixedThreadPool(4);
        try {
            var writes = new ArrayList<CompletableFuture<Void>>();
            for (int id : ids) {
                writes.add(CompletableFuture.supplyAsync(() -> {
                    int start = id * TransferTestSupport.MTU;
                    return this.manager.receive(new P72_FTPPayload("concurrent", id,
                            Arrays.copyOfRange(data, start, Math.min(data.length, start + TransferTestSupport.MTU))));
                }, senders).thenCompose(CompletionStage::toCompletableFuture));
            }
            TransferTestSupport.await(CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)));
            TransferTestSupport.await(task.completion());
            assertEquals(25, task.getReceivedParts());
            assertArrayEquals(data, Files.readAllBytes(task.getFile()));
        } finally {
            senders.shutdownNow();
            assertTrue(senders.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void taskIdsRouteInterleavedPayloadsToDifferentFiles() throws Exception {
        byte[] first = new byte[TransferTestSupport.MTU + 7];
        byte[] second = new byte[TransferTestSupport.MTU + 3];
        Arrays.fill(first, (byte) 11);
        Arrays.fill(second, (byte) 22);
        var a = register("a", first, TransferMode.RANGED);
        var b = register("b", second, TransferMode.RANGED);
        var writes = new CompletableFuture<?>[]{
                this.manager.receive(new P72_FTPPayload("a", 1, Arrays.copyOfRange(first, TransferTestSupport.MTU, first.length))).toCompletableFuture(),
                this.manager.receive(new P72_FTPPayload("b", 0, Arrays.copyOf(second, TransferTestSupport.MTU))).toCompletableFuture(),
                this.manager.receive(new P72_FTPPayload("a", 0, Arrays.copyOf(first, TransferTestSupport.MTU))).toCompletableFuture(),
                this.manager.receive(new P72_FTPPayload("b", 1, Arrays.copyOfRange(second, TransferTestSupport.MTU, second.length))).toCompletableFuture()
        };
        TransferTestSupport.await(CompletableFuture.allOf(writes));
        TransferTestSupport.await(a.completion());
        TransferTestSupport.await(b.completion());
        assertArrayEquals(first, Files.readAllBytes(a.getFile()));
        assertArrayEquals(second, Files.readAllBytes(b.getFile()));
    }

    @Test
    void registrationRejectsDuplicateTaskAndNormalizedDestination() throws Exception {
        register("original", new byte[1], TransferMode.INLINE);
        assertThrows(IllegalArgumentException.class, () -> this.manager.register("original",
                this.directory.resolve("other"), 1, TransferTestSupport.hash(new byte[1]), TransferMode.INLINE, TransferTestSupport.TIMEOUT));
        assertThrows(IllegalArgumentException.class, () -> this.manager.register("other",
                this.directory.resolve("nested/../original"), 1, TransferTestSupport.hash(new byte[1]), TransferMode.INLINE, TransferTestSupport.TIMEOUT));
    }

    @Test
    void rejectsInvalidMetadataAndTaskCapacity() throws Exception {
        String digest = TransferTestSupport.hash(new byte[1]);
        assertThrows(IllegalArgumentException.class, () -> this.manager.register("empty",
                this.directory.resolve("empty"), 0, digest, TransferMode.RANGED, TransferTestSupport.TIMEOUT));
        assertThrows(IllegalArgumentException.class, () -> this.manager.register("large-inline",
                this.directory.resolve("large-inline"), TransferFileReceiver.INLINE_LIMIT + 1L,
                digest, TransferMode.INLINE, TransferTestSupport.TIMEOUT));
        assertThrows(IllegalArgumentException.class, () -> this.manager.register("digest",
                this.directory.resolve("digest"), 1, "not-sha1", TransferMode.INLINE, TransferTestSupport.TIMEOUT));
        assertThrows(IllegalArgumentException.class, () -> this.manager.register("timeout",
                this.directory.resolve("timeout"), 1, digest, TransferMode.INLINE, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> this.manager.register("huge",
                this.directory.resolve("huge"), (long) TransferTestSupport.MTU * TransferFileReceiver.MAX_PART_COUNT + 1,
                digest, TransferMode.RANGED, TransferTestSupport.TIMEOUT));
        var limited = new TransferManager(1, 1, 1);
        try {
            limited.register("one", this.directory.resolve("one"), 1, digest, TransferMode.INLINE, TransferTestSupport.TIMEOUT);
            assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> limited.register("two",
                    this.directory.resolve("two"), 1, digest, TransferMode.INLINE, TransferTestSupport.TIMEOUT));
        } finally {
            limited.close();
            TransferTestSupport.await(limited.termination());
        }
    }

    @Test
    void unknownTaskDoesNotCreateFilesOrAffectExistingTask() throws Exception {
        var task = register("valid", new byte[1], TransferMode.INLINE);
        assertThrows(ExecutionException.class, () -> TransferTestSupport.await(this.manager.receive(
                new P72_FTPPayload("unknown", 0, new byte[1]))));
        assertFalse(this.manager.cancel("unknown"));
        assertEquals(P75_FTPStatusResult.Status.UNKNOWN, this.manager.status("unknown").getStatus());
        assertEquals(TransferTask.State.RECEIVING, task.getState());
        assertFalse(Files.exists(task.getFile()));
    }

    @Test
    void closeIsIdempotentCancelsTasksAndRejectsFurtherWork() throws Exception {
        var first = register("first", new byte[TransferTestSupport.MTU + 1], TransferMode.RANGED);
        var second = register("second", new byte[1], TransferMode.INLINE);
        TransferTestSupport.await(this.manager.receive(new P72_FTPPayload("first", 0, new byte[TransferTestSupport.MTU])));
        this.manager.close();
        this.manager.close();
        TransferTestSupport.await(this.manager.termination());
        assertEquals(TransferTask.State.CANCELLED, first.getState());
        assertEquals(TransferTask.State.CANCELLED, second.getState());
        assertTrue(first.completion().toCompletableFuture().isCompletedExceptionally());
        assertTrue(second.completion().toCompletableFuture().isCompletedExceptionally());
        assertThrows(IllegalStateException.class, () -> register("late", new byte[1], TransferMode.INLINE));
        assertThrows(ExecutionException.class, () -> TransferTestSupport.await(this.manager.receive(
                new P72_FTPPayload("first", 1, new byte[1]))));
        assertFalse(Files.exists(first.getFile()));
    }

    @Test
    void existingWireSmokeSuiteRunsUnderJUnit() throws Exception {
        // Includes actual SimpNet frame/codec, two Channel scheduling, disconnect reassignment,
        // refill after writes, INLINE scheduling rejection, status replay and terminal reports.
        TransferReceiveTest.main(new String[0]);
    }

    private TransferFileReceiver register(String id, byte[] data, TransferMode mode) throws Exception {
        return this.manager.register(id, this.directory.resolve(id), data.length, TransferTestSupport.hash(data), mode, TransferTestSupport.TIMEOUT);
    }
}
