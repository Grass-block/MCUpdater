package me.gb2022.mcupdater.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.protocol.MCUProtocol;
import me.gb2022.mcupdater.protocol.P30_CDNAuthenticate;
import me.gb2022.mcupdater.protocol.P31_CDNAuthenticationResult;
import me.gb2022.mcupdater.protocol.P32_CASInventorySync;
import me.gb2022.mcupdater.rtp.RTPServer;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;

import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Outbound, reverse-RTP CAS publisher. Usage: CDNClient <cas-path> <serverId> <token> <host[:port]>.
 */
public final class CDNClient {
    private static final int DEFAULT_PORT = 65322;

    private CDNClient() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: CDNClient <cas-path> <serverId> <token> <host[:port]>");
        }
        Path casPath = Path.of(args[0]).toAbsolutePath().normalize();
        String serverId = args[1];
        String token = args[2];
        var address = parseAddress(args[3]);
        var storage = new CASResourceStorage(new FilePath(casPath.toString()));
        var rtp = new RTPServer(storage);
        var eventLoop = new NioEventLoopGroup(1);
        try {
            var bootstrap = new Bootstrap().group(eventLoop).channel(NioSocketChannel.class);
            MCUProtocol.clientOptions(bootstrap);
            Channel channel = bootstrap.handler(MCUProtocol.initializer()
                                                           .config(i -> i.handler(() -> new AuthenticationHandler(serverId, token, storage, rtp))))
                                       .connect(address).sync().channel();
            channel.closeFuture().sync();
        } finally {
            rtp.close();
            rtp.termination().toCompletableFuture().get();
            eventLoop.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
            storage.close();
        }
    }

    private static InetSocketAddress parseAddress(String host) {
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end < 0) {
                throw new IllegalArgumentException("Invalid IPv6 host");
            }
            int port = host.length() == end + 1 ? DEFAULT_PORT : Integer.parseInt(host.substring(end + 2));
            return new InetSocketAddress(host.substring(1, end), port);
        }
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            return new InetSocketAddress(host.substring(0, colon), Integer.parseInt(host.substring(colon + 1)));
        }
        return new InetSocketAddress(host, DEFAULT_PORT);
    }

    private static final class AuthenticationHandler extends PacketInboundHandler {
        private final String serverId;
        private final String token;
        private final CASResourceStorage storage;
        private final RTPServer rtp;
        private boolean authenticated;

        private AuthenticationHandler(String serverId, String token, CASResourceStorage storage, RTPServer rtp) {
            this.serverId = serverId;
            this.token = token;
            this.storage = storage;
            this.rtp = rtp;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(new P30_CDNAuthenticate(serverId, token));
            ctx.fireChannelActive();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
            if (packet instanceof P31_CDNAuthenticationResult result) {
                if (authenticated || !result.accepted()) {
                    ctx.close();
                    return;
                }
                authenticated = true;
                ctx.pipeline().addLast("reverse-rtp", rtp.masterHandler());
                var objects = storage.listResources()
                                     .stream()
                                     .map(meta -> new P32_CASInventorySync.ObjectInfo(meta.getSha1(), meta.getLength()))
                                     .toList();
                ctx.writeAndFlush(new P32_CASInventorySync(objects)).addListener(sent -> {
                    if (!sent.isSuccess()) {
                        ctx.close();
                    }
                });
            } else if (authenticated) {
                ctx.fireChannelRead(packet);
            } else {
                ctx.close();
            }
        }
    }
}
