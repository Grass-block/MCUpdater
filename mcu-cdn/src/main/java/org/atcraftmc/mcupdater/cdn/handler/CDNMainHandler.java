package org.atcraftmc.mcupdater.cdn.handler;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.commons.file.FilePath;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.P02_CDNHandShake;
import org.atcgroup.mcupdater.network.packet.P03_CDNHandShakeResponse;
import org.atcgroup.mcupdater.network.packet.P40_CDNFileCheckRequest;
import org.atcgroup.mcupdater.network.packet.P41_CDNFileCheckResult;
import org.atcgroup.mcupdater.util.DiffCheck;
import org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer;

public final class CDNMainHandler extends PacketInboundHandler {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P02_CDNHandShake message) {
            ctx.channel().pipeline().addLast(new CDNFileServerHandler(
                    MCUpdaterCDNServer.FILE_PATH.append(message.getRepo()),
                    MCUpdaterCDNServer.INSTANCE.getFileManager()
            ));
            MCUProtocolV2.sendPacket(ctx, new P03_CDNHandShakeResponse());
            return;
        }

        if (packet instanceof P40_CDNFileCheckRequest request) {
            var sum = MCUpdaterCDNServer.INSTANCE.getRepoChecksumManager(request.getRepo());
            var failed = new P41_CDNFileCheckResult();
            var count = 0;

            for (var file : request.getHashes().keySet()) {
                var remoteHashes = request.getHashes().get(file);
                var localHashes = sum.getFileChecksum(file);

                if (DiffCheck.compare(remoteHashes, localHashes)) {
                    continue;
                }

                failed.addFile(file);
                count++;
            }

            MCUpdaterCDNServer.LOGGER.info(
                    "{} - sync: {} Dirty, {} Up-to-date.",
                    ctx.channel().remoteAddress(),
                    count,
                    request.getHashes().size() - count
            );

            MCUProtocolV2.sendPacket(ctx, failed);
            return;
        }

        ctx.fireChannelRead(packet);
    }
}
