package me.gb2022.mcupdater.protocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import me.gb2022.mcupdater.rtp.protocol.RTPProtocol;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import me.gb2022.simpnet.packet.PacketRegistry;
import org.atcgroup.mcupdater.network.packet.P00_KeepAlive;

public interface MCUProtocol {
    PacketRegistry REGISTRY = new PacketRegistry(256, (i) -> {
        i.register(0x00, P00_KeepAlive.class);
        i.register(0x01, P01_ClientHandshake.class);
        i.register(0x02, P02_ServerHandshake.class);

        i.register(0x10, P10_ChannelHeaderRequest.class);
        i.register(0x11, P11_ChannelHeaders.class);
        i.register(0x12, P12_UpdateRequest.class);
        i.register(0x13, P13_VersionHeaders.class);

        i.register(0x30, P30_CDNAuthenticate.class);
        i.register(0x31, P31_CDNAuthenticationResult.class);
        i.register(0x32, P32_CASInventorySync.class);

        RTPProtocol.init(i);
    });
    int MAX_FRAME_SIZE = 16 * 1024 * 1024;


    static void serverOptions(ServerBootstrap b) {
        b.option(ChannelOption.SO_BACKLOG, 1024);
        b.option(ChannelOption.SO_REUSEADDR, true);

        b.childOption(ChannelOption.TCP_NODELAY, true);
        b.childOption(ChannelOption.SO_KEEPALIVE, true);
        b.childOption(ChannelOption.SO_RCVBUF, 1024 * 1024);
        b.childOption(ChannelOption.SO_SNDBUF, 1024 * 1024);
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        b.childOption(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(16 * 1024, 64 * 1024, 256 * 1024));
        b.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(64 * 1024, 256 * 1024));
    }

    static void clientOptions(Bootstrap b) {
        b.option(ChannelOption.SO_REUSEADDR, true);
        b.option(ChannelOption.TCP_NODELAY, true);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.SO_RCVBUF, 1024 * 1024);
        b.option(ChannelOption.SO_SNDBUF, 1024 * 1024);
        b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        b.option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(16 * 1024, 64 * 1024, 256 * 1024));
        b.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(64 * 1024, 256 * 1024));
    }

    static NettyChannelInitializer initializer() {
        return new NettyChannelInitializer().config((i) -> {
            i.customLengthFrame(MAX_FRAME_SIZE);
            i.packet(REGISTRY);
        });
    }

}
