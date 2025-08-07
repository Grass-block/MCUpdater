package org.atcraftmc.updater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import org.atcraftmc.updater.network.packet.P15_CDNDownloads;
import org.atcraftmc.updater.network.packet.P53_CDNDownloadRequest;

public final class ClientCDNSessionHandler extends MCUNetHandler {
    private final String repo;
    private final P15_CDNDownloads packet;
    private int count = 0;

    public ClientCDNSessionHandler(String repo, P15_CDNDownloads packet) {
        this.repo = repo;
        this.packet = packet;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if(this.packet.getTargets().isEmpty()) {
            ctx.disconnect();
            return;
        }

        ctx.pipeline().get(PatchFileHandler.class).setReceivedCallback((f) -> {
            this.count++;
            if (this.count == this.packet.getTargets().size()) {
                ctx.pipeline().get(PatchFileHandler.class).finish();
                ctx.disconnect();
            }
        });
        ctx.writeAndFlush(new P53_CDNDownloadRequest(this.repo, this.packet.getTargets()));
    }

    @Override
    public void handlePacket(Packet packet, ChannelHandlerContext ctx) {

    }
}
