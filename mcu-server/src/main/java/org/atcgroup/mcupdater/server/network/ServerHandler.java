package org.atcgroup.mcupdater.server.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.server.MCUpdaterServer;
import org.atcgroup.mcupdater.server.file.FileSource;

import java.util.stream.Collectors;

//here's only client connection.
public final class ServerHandler extends PacketInboundHandler {
    public static final Logger LOGGER = LogManager.getLogger("Network");

    private final MCUpdaterServer server;

    public ServerHandler(MCUpdaterServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        var session = this.server.createSession();
        LOGGER.info("New session created: {}", session.getSessionId());
        ctx.writeAndFlush(new P01_ServerHello(session)).addListener(f -> {
            System.out.println("done");

            if (!f.isSuccess()) {
                f.cause().printStackTrace();
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P10_ChannelHeaderRequest) {
            var data = MCUpdaterServer.instance()
                    .getFileService()
                    .sources()
                    .values()
                    .stream()
                    .map(FileSource::meta)
                    .collect(Collectors.toSet());

            ctx.writeAndFlush(new P20_ChannelHeaders(data));
            return;
        }

        if (packet instanceof P11_UpdateRequest request) {
            System.out.println(request);
            return;
        }

        if (packet instanceof P12_UpdateLogRequest request) {
            System.out.println(request);
            return;
        }

        ctx.fireChannelRead(packet);
    }
}
