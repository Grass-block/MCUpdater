package me.gb2022.mcupdater.cdn;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.protocol.P30_CDNAuthenticate;
import me.gb2022.mcupdater.protocol.P31_CDNAuthenticationResult;
import me.gb2022.mcupdater.protocol.P32_CASInventorySync;
import me.gb2022.mcupdater.rtp.RTPClient;
import me.gb2022.mcupdater.rtp.TransferBatchResult;
import me.gb2022.mcupdater.storage.CASResourceStorage;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CDNTransferHandler extends PacketInboundHandler {
    private final Map<String, String> loginRecords;
    private final CASResourceStorage storage;
    private final ExecutorService sync = Executors.newSingleThreadExecutor(r -> new Thread(r, "cdn-cas-sync"));
    private boolean authenticated;
    private boolean syncing;
    private RTPClient client;

    CDNTransferHandler(Map<String, String> loginRecords, CASResourceStorage storage) {
        this.loginRecords = Map.copyOf(loginRecords);
        this.storage = storage;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P30_CDNAuthenticate auth) {
            if (authenticated || syncing) {
                ctx.close();
                return;
            }
            String expected = loginRecords.get(auth.serverId());
            authenticated = expected != null && java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), auth.token()
                                                                                                                                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ctx.writeAndFlush(new P31_CDNAuthenticationResult(authenticated, authenticated ? "accepted" : "invalid serverId or token"))
               .addListener(sent -> {
                   if (!sent.isSuccess() || !authenticated) {
                       ctx.close();
                   }
               });
        } else if (packet instanceof P32_CASInventorySync inventory) {
            if (!authenticated || syncing) {
                ctx.close();
                return;
            }
            syncing = true;
            Path work = Path.of(".cdn-transfer", java.util.UUID.randomUUID().toString()).toAbsolutePath();
            client = new RTPClient(new FilePath(work.toString()), true);
            ctx.pipeline().addLast("cdn-rtp-client", client.masterHandler());
            var rtp = client;
            rtp.runInit().whenComplete((info, error) -> {
                if (error != null) {
                    ctx.close();
                    return;
                }
                sync.execute(() -> synchronize(ctx, rtp, inventory));
            });
        } else {
            ctx.fireChannelRead(packet);
        }
    }

    private void synchronize(ChannelHandlerContext ctx, RTPClient rtp, P32_CASInventorySync inventory) {
        try {
            var missing = new ArrayList<String>();
            for (var object : inventory.objects()) {
                var local = storage.getResourceInfo(object.sha1());
                if (local.isEmpty() || local.get().getLength() != object.size() || storage.getResource(object.sha1())
                                                                                          .isEmpty()) {
                    missing.add(object.sha1());
                }
            }
            for (int offset = 0; offset < missing.size(); offset += 32) {
                var ids = missing.subList(offset, Math.min(offset + 32, missing.size())).toArray(String[]::new);
                TransferBatchResult result = rtp.requestFiles(ids, ignored -> {
                }).get();
                if (!result.missingIds().isEmpty()) {
                    throw new IllegalStateException("Source CAS inventory changed during CDN sync");
                }
                for (var id : ids) {
                    var file = result.files().get(id);
                    if (file == null) {
                        throw new IllegalStateException("RTP omitted CAS object " + id);
                    }
                    storage.registerResource(file.toFile());
                    Files.deleteIfExists(file);
                }
            }
            ctx.close();
        } catch (Exception error) {
            ctx.fireExceptionCaught(error);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (client != null) {
            client.close();
        }
        sync.shutdown();
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
