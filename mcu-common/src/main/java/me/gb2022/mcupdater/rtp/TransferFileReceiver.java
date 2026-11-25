package me.gb2022.mcupdater.rtp;

import me.gb2022.mcupdater.rtp.protocol.RTPProtocol;
import org.atcgroup.mcupdater.util.FileDigest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** A channel-independent receive task. All filesystem access runs on the manager's disk executor. */
public final class TransferFileReceiver implements TransferTask {
    public static final int FILE_MTU = 65536;
    public static final int INLINE_LIMIT = 16 * 1024;
    public static final int MAX_PART_COUNT = 1024 * 1024;

    private final String taskId;
    private final Path file;
    private final long size;
    private final TransferMode mode;
    private final int partCount;
    private final byte[] sha1;
    private final long timeoutNanos;
    private final BitSet received;
    private final Map<Integer, CompletableFuture<Void>> pending = new HashMap<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.RECEIVING);
    private final ReentrantReadWriteLock ioLock = new ReentrantReadWriteLock();
    private final CompletableFuture<Path> completion = new CompletableFuture<>();
    private volatile long lastActivity = System.nanoTime();
    private volatile Throwable failure;
    private volatile Path staging;
    private volatile FileChannel channel;
    private int receivedParts;
    private long receivedBytes;
    private boolean inlineReceived;

    TransferFileReceiver(String taskId, Path file, long size, String sha1, TransferMode mode,
                         Duration timeout) {
        if (taskId == null || taskId.isEmpty() || taskId.length() > 128 || size < 0 || mode == null
                || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Invalid transfer metadata");
        }
        RTPProtocol.validateSha1(sha1);
        if (mode == TransferMode.INLINE && size > INLINE_LIMIT) {
            throw new IllegalArgumentException("INLINE file exceeds limit");
        }
        if (mode == TransferMode.RANGED && (size == 0
                || (size - 1) / FILE_MTU + 1 > MAX_PART_COUNT)) {
            throw new IllegalArgumentException("Invalid range plan; use INLINE for empty files");
        }
        this.taskId = taskId;
        this.file = file.toAbsolutePath().normalize();
        this.size = size;
        this.mode = mode;
        this.partCount = mode == TransferMode.INLINE ? 1 : (int) ((size - 1) / FILE_MTU + 1);
        this.received = mode == TransferMode.RANGED ? new BitSet(this.partCount) : null;
        this.sha1 = HexFormat.of().parseHex(sha1);
        this.timeoutNanos = timeout.toNanos();
    }

    CompletionStage<Void> receive(int index, byte[] data, Executor executor) {
        final long start = (long) index * FILE_MTU;
        final CompletableFuture<Void> result;
        synchronized (this) {
            if (this.state.get() != State.RECEIVING) {
                return CompletableFuture.failedFuture(new IllegalStateException("Task is " + this.state.get()));
            }
            if (data == null || index < 0 || index >= this.partCount) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Range outside file"));
            }
            if (this.mode == TransferMode.INLINE) {
                if (start != 0 || data.length != this.size) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("INLINE requires the entire file"));
                }
            } else {
                if (data.length != Math.min(FILE_MTU, this.size - start)) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Range does not match plan"));
                }
            }
            if (this.mode == TransferMode.INLINE ? this.inlineReceived : this.received.get(index)) {
                return CompletableFuture.completedFuture(null);
            }
            var existing = this.pending.get(index);
            if (existing != null) {
                return existing.minimalCompletionStage();
            }
            result = new CompletableFuture<>();
            this.pending.put(index, result);
        }
        try {
            executor.execute(() -> write(index, start, data, result));
        } catch (RejectedExecutionException e) {
            synchronized (this) {
                this.pending.remove(index);
            }
            result.completeExceptionally(e);
        }
        return result.minimalCompletionStage();
    }

    private void open() throws IOException {
        if (this.channel != null) {
            return;
        }
        this.ioLock.writeLock().lock();
        try {
            if (this.state.get() != State.RECEIVING) {
                throw new IOException("Task no longer receiving");
            }
            if (this.channel == null) {
                Files.createDirectories(this.file.getParent());
                // Staging on the destination filesystem permits an atomic commit.
                this.staging = Files.createTempFile(this.file.getParent(), ".mcu-transfer-", ".part");
                this.channel = FileChannel.open(this.staging, StandardOpenOption.WRITE);
            }
        } finally {
            this.ioLock.writeLock().unlock();
        }
    }

    private void write(int index, long start, byte[] data, CompletableFuture<Void> result) {
        try {
            open();
            this.ioLock.readLock().lock();
            try {
                if (this.state.get() != State.RECEIVING) {
                    throw new IOException("Task no longer receiving");
                }
                var buffer = ByteBuffer.wrap(data);
                long position = start;
                while (buffer.hasRemaining()) {
                    int written = this.channel.write(buffer, position);
                    if (written <= 0) {
                        throw new IOException("File write made no progress");
                    }
                    position += written;
                }
            } finally {
                this.ioLock.readLock().unlock();
            }
            boolean verify;
            synchronized (this) {
                if (this.state.get() != State.RECEIVING) {
                    throw new IOException("Task no longer receiving");
                }
                // Publish completion only after the entire positional write succeeds.
                if (this.mode == TransferMode.INLINE) {
                    this.inlineReceived = true;
                } else {
                    this.received.set(index);
                }
                this.receivedParts++;
                this.receivedBytes += data.length;
                this.lastActivity = System.nanoTime();
                verify = this.receivedParts == this.partCount
                        && this.state.compareAndSet(State.RECEIVING, State.VERIFYING);
            }
            if (verify) {
                verifyAndCommit();
            }
            result.complete(null);
        } catch (Throwable e) {
            terminate(State.FAILED, e);
            result.completeExceptionally(e);
        } finally {
            synchronized (this) {
                this.pending.remove(index);
            }
            cleanup();
        }
    }

    private void verifyAndCommit() throws IOException {
        this.ioLock.writeLock().lock();
        try {
            if (this.state.get() != State.VERIFYING) {
                throw new IOException("Verification cancelled");
            }
            this.channel.force(true);
            this.channel.close();
            this.channel = null;
            if (Files.size(this.staging) != this.size
                    || !MessageDigest.isEqual(this.sha1, FileDigest.calculateSHA1(this.staging.toFile()))) {
                throw new IOException("SHA-1 verification failed: " + this.taskId);
            }
            // This CAS is the commit boundary: cancellation may win until this point.
            if (!this.state.compareAndSet(State.VERIFYING, State.COMMITTING)) {
                throw new IOException("Commit cancelled");
            }
            // Do not fall back to a non-atomic replacement of an existing destination.
            Files.move(this.staging, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            this.staging = null;
            this.state.set(State.COMPLETED);
        } finally {
            this.ioLock.writeLock().unlock();
        }
        this.completion.complete(this.file);
    }

    boolean cancel() {
        return terminate(State.CANCELLED, new CancellationException("Transfer cancelled: " + this.taskId));
    }

    void expire(long now) {
        if (now - this.lastActivity >= this.timeoutNanos) {
            terminate(State.TIMED_OUT, new TimeoutException("Transfer timed out: " + this.taskId));
        }
    }

    private boolean terminate(State target, Throwable cause) {
        while (true) {
            var current = this.state.get();
            if (current != State.RECEIVING && current != State.VERIFYING
                    && !(current == State.COMMITTING && target == State.FAILED)) {
                return false;
            }
            if (this.state.compareAndSet(current, target)) {
                this.failure = cause;
                return true;
            }
        }
    }

    boolean needsCleanup() {
        var current = this.state.get();
        return !this.completion.isDone() && (current == State.CANCELLED || current == State.TIMED_OUT
                || current == State.FAILED) && this.failure != null;
    }

    /** Called only by disk workers; retried by the manager if deletion or queue admission fails. */
    void cleanup() {
        if (!needsCleanup()) {
            return;
        }
        this.ioLock.writeLock().lock();
        try {
            if (this.channel != null) {
                this.channel.close();
                this.channel = null;
            }
            if (this.staging != null) {
                Files.deleteIfExists(this.staging);
                this.staging = null;
            }
            this.completion.completeExceptionally(this.failure);
        } catch (IOException e) {
            this.failure.addSuppressed(e);
        } finally {
            this.ioLock.writeLock().unlock();
        }
    }

    /** Find a missing fixed-size part by its BitSet index, excluding in-flight requests. */
    synchronized int nextMissingPart(BitSet requested) {
        if (this.mode != TransferMode.RANGED || this.state.get() != State.RECEIVING) {
            return -1;
        }
        for (int i = this.received.nextClearBit(0); i < this.partCount;
             i = this.received.nextClearBit(i + 1)) {
            if (!requested.get(i) && !this.pending.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public Path getFile() {
        return this.file;
    }

    public TransferMode getMode() {
        return this.mode;
    }

    public State getState() {
        return this.state.get();
    }

    public synchronized int getReceivedParts() {
        return this.receivedParts;
    }

    public synchronized long getReceivedBytes() {
        return this.receivedBytes;
    }

    public synchronized BitSet getReceivedPartsSnapshot() {
        return this.received == null ? null : (BitSet) this.received.clone();
    }

    public CompletionStage<Path> completion() {
        return this.completion.minimalCompletionStage();
    }
}
