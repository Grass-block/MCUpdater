package org.atcgroup.mcupdater.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketRegistry;
import org.atcgroup.mcupdater.network.packet.*;

public interface MCUProtocolV2 {
    PacketRegistry PACKETS = new PacketRegistry(256, (i) -> {
        i.register(0x00, P00_KeepAlive.class);
        i.register(0x01, P01_ServerHello.class);
        i.register(0x02, P02_CDNHandShake.class);
        i.register(0x03, P03_CDNHandShakeResponse.class);

        i.register(0x10, P10_ChannelHeaderRequest.class);
        i.register(0x11, P11_UpdateRequest.class);
        i.register(0x12, P12_UpdateLogRequest.class);

        i.register(0x20, P20_ChannelHeaders.class);
        i.register(0x21, P21_VersionHeaders.class);
        i.register(0x22, P22_UpdateLogs.class);

        i.register(0x30, P30_FileDownloadRequest.class);
        i.register(0x31, P31_FileDownloadStart.class);
        i.register(0x32, P32_FileDownloadComplete.class);

        i.register(0x40, P40_CDNFileCheckRequest.class);
        i.register(0x41, P41_CDNFileCheckResult.class);

        i.register(0x70, P70_FTPHeader.class);
        i.register(0x71, P71_FTPPayload.class);
        i.register(0x72, P72_FTPPayloadReceived.class);
        i.register(0x73, P73_FTPCancel.class);
        i.register(0x74, P74_FTPComplete.class);
        i.register(0x75, P75_FTPCompleteResponse.class);
    });
    int CDN_PAYLOAD_SIZE = 256 * 1024;//256KiB

    static void sendPacket(ChannelHandlerContext ctx, Packet packet) {
        sendPacket(ctx.channel(), packet);
    }

    static void sendPacket(Channel ch, Packet packet) {
        ch.writeAndFlush(packet).addListener(f -> {
            if (!f.isSuccess()) {
                System.out.println("Failed to send packet: " + packet);
                f.cause().printStackTrace();
            }
        });
    }

    static NettyChannelInitializer initializer() {
        return new NettyChannelInitializer().config((i) -> {
            i.lengthFrame();
            i.compression(1024, 1);
            i.packet(PACKETS);
        });
    }
}
