package org.atcgroup.mcupdater.server.v4.rtp;

import org.atcgroup.mcupdater.server.v4.rtp.TransferFileReceiver;
import org.atcgroup.mcupdater.server.v4.rtp.TransferMode;
import org.atcgroup.mcupdater.server.v4.rtp.TransferTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

import static org.atcgroup.mcupdater.server.v4.rtp.TransferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferFileReceiverTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 16384})
    void inlineCommitsWithoutBitSetOrScheduling(int length) throws Exception {
        byte[] data = bytes(length);
        var task = task(data, TransferMode.INLINE);
        var disk = new ManualExecutor();
        var write = task.receive(0, data, disk);

        assertNull(task.getReceivedPartsSnapshot());
        assertEquals(-1, task.nextMissingPart(new BitSet()));
        assertFalse(Files.exists(task.getFile()), "Receiving must not touch the filesystem on the caller");
        assertFalse(write.toCompletableFuture().isDone());
        disk.runNext();

        await(write);
        assertEquals(task.getFile(), await(task.completion()));
        assertArrayEquals(data, Files.readAllBytes(task.getFile()));
        assertEquals(TransferTask.State.COMPLETED, task.getState());
        noStaging();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 65536, 65537, 131072, 131089})
    void fixedMtuPartsCanArriveInReverseOrder(int length) throws Exception {
        byte[] data = bytes(length);
        var task = task(data, TransferMode.RANGED);
        int count = (length - 1) / MTU + 1;
        for (int id = count - 1; id >= 0; id--) {
            int start = id * MTU;
            await(task.receive(id, Arrays.copyOfRange(data, start, Math.min(length, start + MTU)), Runnable::run));
            assertTrue(task.getReceivedPartsSnapshot().get(id));
        }
        await(task.completion());
        assertEquals(count, task.getReceivedParts());
        assertArrayEquals(data, Files.readAllBytes(task.getFile()), "partId must map to partId * FILE_MTU");
        noStaging();
    }

    @Test
    void bitSetChangesOnlyAfterDiskOperationCompletes() throws Exception {
        var task = task(new byte[MTU + 1], TransferMode.RANGED);
        var disk = new ManualExecutor();
        var write = task.receive(0, new byte[MTU], disk);
        assertEquals(0, task.getReceivedParts());
        assertFalse(task.getReceivedPartsSnapshot().get(0));
        assertFalse(Files.exists(task.getFile()));
        disk.runNext();
        await(write);
        assertEquals(1, task.getReceivedParts());
        assertTrue(task.getReceivedPartsSnapshot().get(0));
        var snapshot = task.getReceivedPartsSnapshot();
        snapshot.clear();
        assertTrue(task.getReceivedPartsSnapshot().get(0), "Snapshot must not expose mutable task state");
        cancelAndClean(task);
    }

    @Test
    void duplicatePendingPartQueuesOnlyOneWrite() throws Exception {
        var task = task(new byte[MTU + 1], TransferMode.RANGED);
        var disk = new ManualExecutor();
        var first = task.receive(0, new byte[MTU], disk);
        var duplicate = task.receive(0, new byte[MTU], disk);
        assertEquals(1, disk.size());
        assertFalse(duplicate.toCompletableFuture().isDone());
        disk.runNext();
        await(first);
        await(duplicate);
        await(task.receive(0, new byte[MTU], disk));
        assertEquals(0, disk.size());
        assertEquals(1, task.getReceivedParts());
        cancelAndClean(task);
    }

    @Test
    void invalidPartIdsAndLengthsCannotChangeProgress() throws Exception {
        var task = task(new byte[MTU + 1], TransferMode.RANGED);
        var disk = new ManualExecutor();
        assertThrows(ExecutionException.class, () -> await(task.receive(-1, new byte[1], disk)));
        assertThrows(ExecutionException.class, () -> await(task.receive(2, new byte[1], disk)));
        assertThrows(ExecutionException.class, () -> await(task.receive(0, new byte[MTU - 1], disk)));
        assertThrows(ExecutionException.class, () -> await(task.receive(1, new byte[MTU], disk)));
        assertThrows(ExecutionException.class, () -> await(task.receive(1, new byte[0], disk)));
        assertThrows(ExecutionException.class, () -> await(task.receive(0, null, disk)));
        assertEquals(0, disk.size());
        assertEquals(0, task.getReceivedParts());
        assertEquals(TransferTask.State.RECEIVING, task.getState());
        cancelAndClean(task);
    }

    @Test
    void rejectedWriteCanBeRetriedWithoutLosingThePart() throws Exception {
        var task = task(new byte[1], TransferMode.INLINE);
        var failure = assertThrows(ExecutionException.class, () -> await(task.receive(0, new byte[1], command -> {
            throw new RejectedExecutionException("test queue full");
        })));
        assertInstanceOf(RejectedExecutionException.class, failure.getCause());
        assertEquals(0, task.getReceivedParts());
        await(task.receive(0, new byte[1], Runnable::run));
        await(task.completion());
    }

    @Test
    void hashFailurePreservesExistingDestinationAndDeletesStaging() throws Exception {
        var task = task(new byte[]{1}, TransferMode.INLINE);
        Files.writeString(task.getFile(), "existing destination");
        assertThrows(ExecutionException.class, () -> await(task.receive(0, new byte[]{2}, Runnable::run)));
        assertThrows(ExecutionException.class, () -> await(task.completion()));
        assertEquals(TransferTask.State.FAILED, task.getState());
        assertEquals("existing destination", Files.readString(task.getFile()));
        noStaging();
    }

    @Test
    void cancellingBeforeQueuedWritePreventsFileCreation() throws Exception {
        var task = task(new byte[1], TransferMode.INLINE);
        var disk = new ManualExecutor();
        var write = task.receive(0, new byte[1], disk);
        assertTrue(task.cancel());
        disk.runNext();
        assertThrows(ExecutionException.class, () -> await(write));
        assertTrue(task.completion().toCompletableFuture().isCompletedExceptionally());
        assertEquals(TransferTask.State.CANCELLED, task.getState());
        assertFalse(Files.exists(task.getFile()));
        noStaging();
    }

    @Test
    void timeoutAfterPartialWriteCleansResourcesWithoutCommitting() throws Exception {
        var task = task(new byte[MTU + 1], TransferMode.RANGED);
        await(task.receive(0, new byte[MTU], Runnable::run));
        task.expire(System.nanoTime() + TIMEOUT.toNanos() + 1);
        task.cleanup();
        var failure = assertThrows(ExecutionException.class, () -> await(task.completion()));
        assertInstanceOf(java.util.concurrent.TimeoutException.class, failure.getCause());
        assertEquals(TransferTask.State.TIMED_OUT, task.getState());
        assertFalse(Files.exists(task.getFile()));
        noStaging();
    }

    @Test
    void schedulerSkipsReceivedPendingAndRequestedIds() throws Exception {
        var task = task(new byte[MTU * 4], TransferMode.RANGED);
        await(task.receive(0, new byte[MTU], Runnable::run));
        var disk = new ManualExecutor();
        task.receive(1, new byte[MTU], disk);
        var requested = new BitSet();
        requested.set(2);
        assertEquals(3, task.nextMissingPart(requested));
        task.cancel();
        disk.runNext();
        task.cleanup();
        noStaging();
    }

    private TransferFileReceiver task(byte[] data, TransferMode mode) throws Exception {
        return new TransferFileReceiver("task", this.directory.resolve("target.bin"), data.length,
                hash(data), mode, TIMEOUT);
    }

    private static byte[] bytes(int length) {
        var data = new byte[length];
        new java.util.Random(42).nextBytes(data);
        return data;
    }

    private void cancelAndClean(TransferFileReceiver task) throws Exception {
        task.cancel();
        task.cleanup();
        assertTrue(task.completion().toCompletableFuture().isCompletedExceptionally());
        noStaging();
    }

    private void noStaging() throws Exception {
        try (var paths = Files.list(this.directory)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }
}
