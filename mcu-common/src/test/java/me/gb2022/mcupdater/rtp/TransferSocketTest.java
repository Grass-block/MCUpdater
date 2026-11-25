package me.gb2022.mcupdater.rtp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.mcupdater.rtp.protocol.P70_FTPRangeRequest;
import me.gb2022.mcupdater.rtp.protocol.P72_FTPPayload;
import me.gb2022.mcupdater.rtp.protocol.P75_FTPStatusResult;
import me.gb2022.mcupdater.rtp.protocol.RTPProtocol;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static me.gb2022.mcupdater.rtp.TransferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferSocketTest {
    @TempDir
    Path directory;

    @Test
    void realInitializerPullsPartsAndReportsCompletionOverLoopbackTcp() throws Exception {
        var manager = new TransferManager();
        var boss = new NioEventLoopGroup(1);
        var workers = new NioEventLoopGroup(1);
        var clients = new NioEventLoopGroup(1);
        var accepted = new CompletableFuture<Channel>();
        var reported = new CompletableFuture<P75_FTPStatusResult>();
        Channel listener = null;
        Channel sender = null;
        byte[] data = new byte[MTU * 12 + 31];
        new java.util.Random(89).nextBytes(data);
        try {
            var task = manager.register("socket", this.directory.resolve("socket.bin"), data.length,
                    hash(data), TransferMode.RANGED, TIMEOUT);
            listener = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new TransferChannelInitializer(manager));
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    accepted.complete(ctx.channel());
                                    ctx.fireChannelActive();
                                }
                            });
                        }
                    }).bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();

            var senderInitializer = new NettyChannelInitializer();
            senderInitializer.customLengthFrame(RTPProtocol.MAX_FRAME_SIZE);
            senderInitializer.packet(RTPProtocol.PACKETS);
            senderInitializer.handler(() -> new PacketInboundHandler() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
                    if (packet instanceof P70_FTPRangeRequest request) {
                        int start = request.getPartId() * MTU;
                        ctx.writeAndFlush(new P72_FTPPayload(request.getTaskId(), request.getPartId(),
                                Arrays.copyOfRange(data, start, Math.min(data.length, start + MTU))));
                    } else if (packet instanceof P75_FTPStatusResult result) {
                        reported.complete(result);
                    } else {
                        reported.completeExceptionally(new AssertionError("Unexpected receiver packet: " + packet));
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    reported.completeExceptionally(cause);
                    ctx.close();
                }
            });
            sender = new Bootstrap().group(clients).channel(NioSocketChannel.class)
                    .handler(senderInitializer).connect(listener.localAddress()).sync().channel();

            await(manager.requestRanges("socket", await(accepted), 4));
            var result = await(reported);
            assertEquals("socket", result.getTaskId());
            assertEquals(P75_FTPStatusResult.Status.COMPLETED, result.getStatus());
            await(task.completion());
            assertArrayEquals(data, Files.readAllBytes(task.getFile()));
            assertEquals(13, task.getReceivedParts());
        } finally {
            if (sender != null) {
                sender.close().syncUninterruptibly();
            }
            if (listener != null) {
                listener.close().syncUninterruptibly();
            }
            if (accepted.isDone() && !accepted.isCompletedExceptionally()) {
                accepted.join().close().syncUninterruptibly();
            }
            manager.close();
            // Start all shutdowns before waiting, so cleanup still proceeds if a wait fails.
            var clientShutdown = clients.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            var workerShutdown = workers.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            var bossShutdown = boss.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            await(manager.termination());
            assertTrue(clientShutdown.await(10, TimeUnit.SECONDS));
            assertTrue(workerShutdown.await(10, TimeUnit.SECONDS));
            assertTrue(bossShutdown.await(10, TimeUnit.SECONDS));
        }
    }
}
