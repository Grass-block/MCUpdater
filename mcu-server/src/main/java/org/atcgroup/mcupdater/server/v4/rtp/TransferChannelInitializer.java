package org.atcgroup.mcupdater.server.v4.rtp;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import me.gb2022.simpnet.channel.NettyChannelInitializer;
import org.atcgroup.mcupdater.server.v4.rtp.protocol.RTPProtocol;

import java.util.Objects;

public final class TransferChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final TransferManager manager;

    public TransferChannelInitializer(TransferManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        var initializer = new NettyChannelInitializer();
        initializer.customLengthFrame(RTPProtocol.MAX_FRAME_SIZE);
        initializer.packet(RTPProtocol.PACKETS);
        initializer.handler(() -> new FileReceiver(this.manager));
        channel.pipeline().addLast("transfer", initializer);
    }
}
