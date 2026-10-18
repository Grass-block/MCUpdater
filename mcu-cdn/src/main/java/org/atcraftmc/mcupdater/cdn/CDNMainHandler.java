package org.atcraftmc.mcupdater.cdn;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.handler.FileServerHandler;
import org.atcgroup.mcupdater.network.packet.P02_CDNHandShake;
import org.atcgroup.mcupdater.network.packet.P03_CDNHandShakeResponse;
import org.atcgroup.mcupdater.network.packet.P40_CDNFileCheckRequest;
import org.atcgroup.mcupdater.network.packet.P41_CDNFileCheckResult;
import org.atcgroup.mcupdater.util.DiffCheck;

public final class CDNMainHandler extends PacketInboundHandler {
    private final int upgradeHttpPort;

    public CDNMainHandler(int upgradeHttpPort) {
        this.upgradeHttpPort = upgradeHttpPort;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P02_CDNHandShake message) {
            ctx.channel().pipeline().addLast(new FileServerHandler(
                    MCUpdaterCDNServer.INSTANCE.getRepository(message.getRepo()),
                    this.upgradeHttpPort,message.getRepo()
            ));
            MCUProtocolV2.sendPacket(ctx, new P03_CDNHandShakeResponse());
            return;
        }

        if (packet instanceof P40_CDNFileCheckRequest request) {
            var sum = MCUpdaterCDNServer.INSTANCE.getRepository(request.getRepo());
            var failed = new P41_CDNFileCheckResult();
            var count = 0;

            for (var file : request.getHashes().keySet()) {
                var remoteHashes = request.getHashes().get(file);
                var localHashes = sum.getFileInfo(file);

                if (localHashes == null) {
                    failed.addFile(file);
                    count++;
                    continue;
                }

                if (DiffCheck.compare(remoteHashes, localHashes.getSha1Raw())) {
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
