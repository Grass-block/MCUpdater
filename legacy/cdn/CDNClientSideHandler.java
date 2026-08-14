package org.atcraftmc.mcupdater.cdn.legacy;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcraftmc.mcupdater.cdn.FileStatusManager;
import org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer;
import org.atcgroup.mcupdater.util.Async;
import org.atcraftmc.mcupdater.cdn.handler.CDNFileServerHandler;
import org.atcraftmc.updater.network.packet.P13_PatchFileInfo;
import org.atcraftmc.updater.network.packet.P14_PatchFileSlice;
import org.atcraftmc.updater.network.packet.P53_CDNDownloadRequest;
import org.atcraftmc.updater.network.packet.P56_CDNDownloadRequestV2;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import static org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer.LOGGER;

public final class CDNClientSideHandler extends PacketInboundHandler {
    private final FileStatusManager fileManager;

    public CDNClientSideHandler(FileStatusManager fileManager) {
        this.fileManager = fileManager;
    }

    private void sendPatchFile(File file, ChannelHandlerContext ctx) throws Exception {
        var max_velocity = Integer.parseInt(MCUpdaterCDNServer.INSTANCE.config().getProperty("max-velocity", "1048576"));
        var velocity_hit_delay = Integer.parseInt(MCUpdaterCDNServer.INSTANCE.config().getProperty("velocity-hit-delay", "0"));

        this.fileManager.addReadLock(file,ctx.channel().remoteAddress().toString());

        Thread.sleep(10);

        MCUProtocolV2.sendPacket(ctx, new P13_PatchFileInfo((int) file.length(), file.lastModified(), ""));

        Thread.sleep(10);

        var buffer = new byte[8192];
        var fin = new FileInputStream(file);
        var bin = new BufferedInputStream(fin);
        var length = 0;

        var count = 0;

        while ((length = bin.read(buffer)) != -1) {
            var data = new byte[length];
            System.arraycopy(buffer, 0, data, 0, length);
            MCUProtocolV2.sendPacket(ctx, new P14_PatchFileSlice(data, 0)).get();

            count += length;

            if (count > max_velocity) {
                Thread.sleep(velocity_hit_delay);
                count = 0;
            }
        }

        MCUProtocolV2.sendPacket(ctx, new P14_PatchFileSlice(new byte[0], P14_PatchFileSlice.SIG_END));

        this.fileManager.removeReadLock(file,ctx.channel().remoteAddress().toString());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        this.fileManager.removeReadLocks(ctx.channel().remoteAddress().toString());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P56_CDNDownloadRequestV2 req) {
            var repo = req.getRepo();
            var files = new ArrayList<File>();
            var task = new CDNFileServerHandler.CDNLockableDownloadHandler(ctx.channel().remoteAddress().toString(), "__client", files, this.fileManager);

            for (var name : req.getTargets()) {
                var path = System.getProperty("user.dir") + "/" + repo + "/" + name + ".zip";
                var file = new File(path);

                if (!file.exists() || file.length() == 0) {
                    continue;
                }

                files.add(file);
            }

            ctx.pipeline().remove("mcu:download");
            ctx.pipeline().addLast("mcu:download", task);

            try {
                task.push();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return;
        }

        if (packet instanceof P53_CDNDownloadRequest req) {
            var repo = req.getRepo();
            Async.WORKER.submit(() -> {
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

            return;
        }

        ctx.fireChannelRead(packet);
    }
}
