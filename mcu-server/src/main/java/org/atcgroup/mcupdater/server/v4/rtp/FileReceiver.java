package org.atcgroup.mcupdater.server.v4.rtp;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.*;

import java.util.HashMap;
import java.util.Map;

public final class FileReceiver extends PacketInboundHandler {
    private static final int MAX_PENDING_WRITES = 8;
    private final TransferManager manager;
    private int pendingWrites;
    private final Map<String, Runnable> subscriptions = new HashMap<>();

    public FileReceiver(TransferManager manager) {
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P70_FTPRangeRequest) {
            // Range requests travel from this receiver to the remote sender, never the reverse.
            ctx.close();
        } else if (packet instanceof P74_FTPStatusRequest request) {
            var status = this.manager.status(request.getTaskId());
            if (status.getStatus() == P75_FTPStatusResult.Status.RECEIVING
                    || status.getStatus() == P75_FTPStatusResult.Status.VERIFYING
                    || status.getStatus() == P75_FTPStatusResult.Status.COMMITTING) {
                ctx.writeAndFlush(status);
                watch(ctx, request.getTaskId());
            } else {
                ctx.writeAndFlush(status);
            }
        } else if (packet instanceof P72_FTPPayload part) {
            watch(ctx, part.getTaskId());
            this.pendingWrites++;
            if (this.pendingWrites >= MAX_PENDING_WRITES) {
                ctx.channel().config().setAutoRead(false);
            }
            this.manager.receive(part).whenComplete((ignored, error) -> {
                try {
                    ctx.executor().execute(() -> {
                        this.pendingWrites--;
                        if (error != null) {
                            var status = this.manager.status(part.getTaskId()).getStatus();
                            if (status != P75_FTPStatusResult.Status.FAILED
                                    && status != P75_FTPStatusResult.Status.CANCELLED
                                    && status != P75_FTPStatusResult.Status.TIMED_OUT) {
                                ctx.fireExceptionCaught(error);
                                ctx.close();
                            }
                            // Terminal failures are reported by the subscription after cleanup.
                            // Keep this Channel available for other tasks.
                            if (ctx.channel().isActive() && this.pendingWrites < MAX_PENDING_WRITES) {
                                ctx.channel().config().setAutoRead(true);
                            }
                        } else if (ctx.channel().isActive() && this.pendingWrites < MAX_PENDING_WRITES) {
                            ctx.channel().config().setAutoRead(true);
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException ignoredRejection) {
                    // The EventLoop has already stopped. Tasks remain owned by the manager.
                    ctx.channel().close();
                }
            });
        } else if (packet instanceof P73_FTPCancel cancel) {
            watch(ctx, cancel.getTaskId());
            this.manager.cancel(cancel.getTaskId());
        } else {
            ctx.fireChannelRead(packet);
        }
    }

    void requestRanges(ChannelHandlerContext ctx, String taskId, int window) {
        this.manager.addWorker(taskId, ctx.channel(), window);
        watch(ctx, taskId);
    }

    private void watch(ChannelHandlerContext ctx, String taskId) {
        if (this.subscriptions.containsKey(taskId)) {
            return;
        }
        var unsubscribe = this.manager.subscribe(taskId, result -> {
            try {
                ctx.executor().execute(() -> {
                    var subscription = this.subscriptions.remove(taskId);
                    if (subscription != null) {
                        subscription.run();
                    }
                    if (ctx.channel().isActive()) {
                        ctx.writeAndFlush(result);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // Channel teardown detaches subscriptions; the task is independent of this Channel.
            }
        });
        this.subscriptions.put(taskId, unsubscribe);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        unsubscribe();
        this.manager.channelDisconnected(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        unsubscribe();
        this.manager.channelDisconnected(ctx.channel());
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            this.manager.channelWritable(ctx.channel());
        }
        ctx.fireChannelWritabilityChanged();
    }

    private void unsubscribe() {
        this.subscriptions.values().forEach(Runnable::run);
        this.subscriptions.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    // No channelInactive cancellation: other channels may still be receiving this task.
}
