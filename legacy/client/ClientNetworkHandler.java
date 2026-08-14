package org.atcraftmc.updater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcraftmc.updater.network.CDNClient;
import org.atcraftmc.updater.client.Event;
import org.atcraftmc.updater.client.util.DeferredTaskManager;
import org.atcgroup.mcupdater.client.util.Log;
import org.atcraftmc.updater.network.packet.*;

import java.util.Objects;

public final class ClientNetworkHandler extends MCUNetHandler {

    @Override
    public void handlePacket(Packet packet, ChannelHandlerContext ctx) {
        if (packet instanceof P10_VersionInfo p) {
            runTask(() -> {
                MCUProtocolV2.sendPacket(ctx, new P02_ClientConversationEnd());
                ctx.disconnect();
                DeferredTaskManager.batch();
                callEvent(Event.RECEIVE_VERSION, p);
            });
        }
        if (packet instanceof P0F_ServerProgressUpdate p) {
            callEvent(Event.PROGRESS, "[服务器] " + p.getData());
        }
        if (packet instanceof P15_CDNDownloads p) {
            if (Objects.equals(p.getHost(), "_")) {
                Log.info("No cdn server specified, skipping CDN download phase.");
                MCUProtocolV2.sendPacket(ctx, new P16_CDNDownloadComplete(p.getSessionId()));
                return;
            }

            new Thread(() -> {
                Log.info("Connecting to CDN (%s:%s)...".formatted(p.getHost(), p.getPort()));
                var client = new CDNClient(p.getHost(), p.getPort()).addHandler(PatchFileHandler::new)
                        .addHandler(() -> new ClientCDNSessionHandler(p.getRepo(), p));
                client.run();
                Log.info("CDN download task complete, finishing global task.");
                MCUProtocolV2.sendPacket(ctx, new P16_CDNDownloadComplete(p.getSessionId()));
            }).start();
        }
    }
}
