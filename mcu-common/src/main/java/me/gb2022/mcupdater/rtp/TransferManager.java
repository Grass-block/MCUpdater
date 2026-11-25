package me.gb2022.mcupdater.rtp;

import io.netty.channel.Channel;
import me.gb2022.mcupdater.rtp.protocol.P70_FTPRangeRequest;
import me.gb2022.mcupdater.rtp.protocol.P72_FTPPayload;
import me.gb2022.mcupdater.rtp.protocol.P75_FTPStatusResult;
import me.gb2022.mcupdater.rtp.protocol.RTPProtocol;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Shared by all Data Channels. Task registration is a trusted local API, not a wire operation.
 */
public final class TransferManager implements AutoCloseable {
    private final Map<String, TransferFileReceiver> tasks = new HashMap<>();
    private final Set<Path> destinations = new HashSet<>();
    private final Set<String> cleaning = new HashSet<>();
    private final Map<String, Set<Consumer<P75_FTPStatusResult>>> listeners = new HashMap<>();
    private final Map<String, CompletedResult> results = new LinkedHashMap<>();
    // Scheduling belongs to the manager, never to the channel-independent task.
    private final Map<String, Map<Channel, Integer>> workers = new HashMap<>();
    private final Map<String, Map<Integer, Channel>> requestedParts = new HashMap<>();
    private final Map<String, BitSet> requestedPartIds = new HashMap<>();
    private static final long RESULT_RETENTION_NANOS = Duration.ofMinutes(5).toNanos();

    private record CompletedResult(P75_FTPStatusResult packet, long completedAt) {
    }

    private final ThreadPoolExecutor diskExecutor;
    private final ScheduledExecutorService timer;
    private final int maxTasks;
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private boolean closed;

    public TransferManager() {
        this(4, 64, 1024);
    }

    public TransferManager(int diskThreads, int queueCapacity, int maxTasks) {
        if (diskThreads <= 0 || queueCapacity <= 0 || maxTasks <= 0) {
            throw new IllegalArgumentException("Executor and task limits must be positive");
        }
        this.maxTasks = maxTasks;
        var sequence = new AtomicInteger();
        this.diskExecutor = new ThreadPoolExecutor(diskThreads, diskThreads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity), runnable -> new Thread(runnable, "transfer-disk-" + sequence.incrementAndGet()), new ThreadPoolExecutor.AbortPolicy()) {
            @Override
            protected void terminated() {
                termination.complete(null);
            }
        };
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "transfer-timeout"));
        this.timer.scheduleWithFixedDelay(this::sweep, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized TransferFileReceiver register(String taskId, Path file, long size, String sha1, TransferMode mode, Duration timeout) {
        if (this.closed) {
            throw new IllegalStateException("Transfer manager is closed");
        }
        var task = new TransferFileReceiver(taskId, file, size, sha1, mode, timeout);
        if (this.tasks.size() >= this.maxTasks) {
            throw new RejectedExecutionException("Too many transfer tasks");
        }
        pruneResults(System.nanoTime());
        if (this.tasks.containsKey(taskId) || this.results.containsKey(taskId) || this.destinations.contains(task.getFile())) {
            throw new IllegalArgumentException("Task id or destination already in use");
        }
        this.tasks.put(taskId, task);
        this.destinations.add(task.getFile());
        task.completion().whenComplete((path, error) -> remove(task));
        return task;
    }

    public CompletionStage<Void> receive(P72_FTPPayload packet) {
        final TransferFileReceiver task;
        synchronized (this) {
            task = this.closed ? null : this.tasks.get(packet.getTaskId());
        }
        if (task == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown or closed transfer task"));
        }
        var result = task.receive(packet.getPartId(), packet.getData(), this.diskExecutor);
        result.whenComplete((ignored, error) -> {
            synchronized (TransferManager.this) {
                var requested = this.requestedParts.get(packet.getTaskId());
                if (requested != null) {
                    requested.remove(packet.getPartId());
                    this.requestedPartIds.get(packet.getTaskId()).clear(packet.getPartId());
                    if (error == null) {
                        dispatch(packet.getTaskId());
                    }
                }
            }
        });
        return result;
    }

    /**
     * Locally enlist a Data Channel; the receiver then requests ranges from its remote sender.
     */
    public CompletionStage<Void> requestRanges(String taskId, Channel channel, int window) {
        var result = new CompletableFuture<Void>();
        try {
            channel.eventLoop().execute(() -> {
                try {
                    var context = channel.pipeline().context(FileReceiver.class);
                    if (context == null || !channel.isActive()) {
                        throw new IllegalArgumentException("Channel has no active FileReceiver");
                    }
                    ((FileReceiver) context.handler()).requestRanges(context, taskId, window);
                    result.complete(null);
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(e);
        }
        return result.minimalCompletionStage();
    }

    synchronized void addWorker(String taskId, Channel channel, int window) {
        var task = this.tasks.get(taskId);
        if (this.closed || task == null || task.getState() != TransferTask.State.RECEIVING) {
            throw new IllegalStateException("Task is not receiving");
        }
        if (task.getMode() != TransferMode.RANGED) {
            throw new IllegalArgumentException("INLINE does not use range scheduling");
        }
        if (window <= 0 || window > 8) {
            throw new IllegalArgumentException("Range window must be between 1 and 8");
        }
        this.workers.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>()).put(channel, window);
        this.requestedParts.computeIfAbsent(taskId, ignored -> new HashMap<>());
        this.requestedPartIds.computeIfAbsent(taskId, ignored -> new BitSet());
        dispatch(taskId);
    }

    private void dispatch(String taskId) {
        var task = this.tasks.get(taskId);
        var channels = this.workers.get(taskId);
        var requested = this.requestedParts.get(taskId);
        if (this.closed || task == null || channels == null || requested == null || task.getState() != TransferTask.State.RECEIVING) {
            return;
        }
        var requestedIds = this.requestedPartIds.get(taskId);
        for (var entry : List.copyOf(channels.entrySet())) {
            var channel = entry.getKey();
            if (!channel.isActive() || !channel.isWritable()) {
                continue;
            }
            long outstanding = requested.values().stream().filter(channel::equals).count();
            while (outstanding < entry.getValue()) {
                int partId = task.nextMissingPart(requestedIds);
                if (partId < 0) {
                    break;
                }
                requested.put(partId, channel);
                requestedIds.set(partId);
                outstanding++;
                channel.writeAndFlush(new P70_FTPRangeRequest(taskId, partId)).addListener(future -> {
                    if (!future.isSuccess()) {
                        channel.close();
                    }
                });
            }
        }
    }

    synchronized void channelDisconnected(Channel channel) {
        for (var entry : this.workers.entrySet()) {
            entry.getValue().remove(channel);
            var requested = this.requestedParts.get(entry.getKey());
            var requestedIds = this.requestedPartIds.get(entry.getKey());
            requested.entrySet().removeIf(part -> {
                if (channel.equals(part.getValue())) {
                    requestedIds.clear(part.getKey());
                    return true;
                }
                return false;
            });
            dispatch(entry.getKey());
        }
    }

    synchronized void channelWritable(Channel channel) {
        for (var entry : this.workers.entrySet()) {
            if (entry.getValue().containsKey(channel)) {
                dispatch(entry.getKey());
            }
        }
    }

    public synchronized TransferTask getTask(String taskId) {
        return this.tasks.get(taskId);
    }

    public boolean cancel(String taskId) {
        final TransferFileReceiver task;
        synchronized (this) {
            task = this.tasks.get(taskId);
        }
        if (task == null || !task.cancel()) {
            return false;
        }
        clean(task);
        return true;
    }

    public synchronized P75_FTPStatusResult status(String taskId) {
        RTPProtocol.validateTaskId(taskId);
        var task = this.tasks.get(taskId);
        if (task != null) {
            return new P75_FTPStatusResult(taskId, P75_FTPStatusResult.Status.valueOf(task.getState().name()));
        }
        pruneResults(System.nanoTime());
        var result = this.results.get(taskId);
        return result == null ? new P75_FTPStatusResult(taskId, P75_FTPStatusResult.Status.UNKNOWN) : result.packet();
    }

    /**
     * The returned action detaches the listener without affecting the receive task.
     */
    synchronized Runnable subscribe(String taskId, Consumer<P75_FTPStatusResult> listener) {
        if (!this.tasks.containsKey(taskId)) {
            listener.accept(status(taskId));
            return () -> {
            };
        }
        this.listeners.computeIfAbsent(taskId, ignored -> new HashSet<>()).add(listener);
        return () -> {
            synchronized (TransferManager.this) {
                var subscribers = this.listeners.get(taskId);
                if (subscribers != null) {
                    subscribers.remove(listener);
                    if (subscribers.isEmpty()) {
                        this.listeners.remove(taskId);
                    }
                }
            }
        };
    }

    private void remove(TransferFileReceiver task) {
        final List<Consumer<P75_FTPStatusResult>> subscribers;
        var packet = new P75_FTPStatusResult(task.getTaskId(), P75_FTPStatusResult.Status.valueOf(task.getState()
                                                                                                      .name()));
        synchronized (this) {
            if (!this.tasks.remove(task.getTaskId(), task)) {
                return;
            }
            this.destinations.remove(task.getFile());
            this.workers.remove(task.getTaskId());
            this.requestedParts.remove(task.getTaskId());
            this.requestedPartIds.remove(task.getTaskId());
            this.results.put(task.getTaskId(), new CompletedResult(packet, System.nanoTime()));
            pruneResults(System.nanoTime());
            while (this.results.size() > this.maxTasks) {
                this.results.remove(this.results.keySet().iterator().next());
            }
            var registered = this.listeners.remove(task.getTaskId());
            subscribers = registered == null ? List.of() : List.copyOf(registered);
            shutdownIfDrained();
        }
        subscribers.forEach(listener -> listener.accept(packet));
    }

    private void pruneResults(long now) {
        this.results.values().removeIf(result -> now - result.completedAt() >= RESULT_RETENTION_NANOS);
    }

    private void sweep() {
        final TransferFileReceiver[] snapshot;
        synchronized (this) {
            pruneResults(System.nanoTime());
            snapshot = this.tasks.values().toArray(TransferFileReceiver[]::new);
        }
        long now = System.nanoTime();
        for (var task : snapshot) {
            task.expire(now);
            clean(task);
        }
    }

    private void clean(TransferFileReceiver task) {
        synchronized (this) {
            if (!task.needsCleanup() || !this.cleaning.add(task.getTaskId())) {
                return;
            }
        }
        try {
            this.diskExecutor.execute(() -> {
                try {
                    task.cleanup();
                } finally {
                    synchronized (TransferManager.this) {
                        cleaning.remove(task.getTaskId());
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            synchronized (this) {
                this.cleaning.remove(task.getTaskId());
            }
            // The periodic sweep retries admission. Never run disk cleanup on the caller/EventLoop.
        }
    }

    private void shutdownIfDrained() {
        if (this.closed && this.tasks.isEmpty()) {
            this.timer.shutdown();
            this.diskExecutor.shutdown();
        }
    }

    /**
     * Nonblocking: queued IO drains and cleanup continues on disk workers before shutdown.
     */
    @Override
    public void close() {
        synchronized (this) {
            this.closed = true;
            this.tasks.values().forEach(TransferFileReceiver::cancel);
            shutdownIfDrained();
        }
        sweep();
    }

    public CompletionStage<Void> termination() {
        return this.termination.minimalCompletionStage();
    }
}
