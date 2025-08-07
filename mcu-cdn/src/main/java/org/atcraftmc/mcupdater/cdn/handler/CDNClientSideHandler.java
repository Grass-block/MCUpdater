package org.atcraftmc.mcupdater.cdn.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcraftmc.mcupdater.cdn.FileLockManager;
import org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer;
import org.atcraftmc.updater.network.packet.P13_PatchFileInfo;
import org.atcraftmc.updater.network.packet.P14_PatchFileSlice;
import org.atcraftmc.updater.network.packet.P53_CDNDownloadRequest;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

import static org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer.LOGGER;

public final class CDNClientSideHandler extends PacketInboundHandler {
    private void sendPatchFile(File file, ChannelHandlerContext ctx) throws Exception {
        var max_velocity = Integer.parseInt(MCUpdaterCDNServer.INSTANCE.config().getProperty("max-velocity", "1048576"));
        var velocity_hit_delay = Integer.parseInt(MCUpdaterCDNServer.INSTANCE.config().getProperty("velocity-hit-delay", "0"));
        FileLockManager.tryLock(file, ctx.channel().remoteAddress());

        Thread.sleep(10);

        ctx.writeAndFlush(new P13_PatchFileInfo((int) file.length(), file.lastModified(), ""));

        Thread.sleep(10);

        var buffer = new byte[8192];
        var fin = new FileInputStream(file);
        var bin = new BufferedInputStream(fin);
        var length = 0;

        var count = 0;

        while ((length = bin.read(buffer)) != -1) {
            var data = new byte[length];
            System.arraycopy(buffer, 0, data, 0, length);
            ctx.writeAndFlush(new P14_PatchFileSlice(data, 0)).get();

            count += length;

            if (count > max_velocity) {
                Thread.sleep(velocity_hit_delay);
                count = 0;
            }
        }

        ctx.writeAndFlush(new P14_PatchFileSlice(new byte[0], P14_PatchFileSlice.SIG_END));

        FileLockManager.unlock(file, ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        FileLockManager.unlockClient(ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P53_CDNDownloadRequest req) {
            var repo = req.getRepo();
            MCUpdaterCDNServer.WORKER.submit(() -> {
                for (var name : req.getTargets()) {
                    var path = System.getProperty("user.dir") + "/" + repo + "/" + name + ".zip";

                    LOGGER.info("responding to {}:{} -> {}", repo, name, path);

                    var file = new File(path);

                    if (!file.exists() || file.length() == 0) {
                        continue;
                    }

                    try {
                        sendPatchFile(file, ctx);
                    } catch (Exception e) {
                        LOGGER.catching(e);
                    }
                }
            });
        }

        ctx.fireChannelRead(packet);
    }
}
