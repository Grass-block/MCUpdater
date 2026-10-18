package org.atcgroup.mcupdater.client.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.client.ClientError;
import org.atcgroup.mcupdater.client.MCUpdaterClient;
import org.atcgroup.mcupdater.network.ErrorCatchHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.*;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Consumer;

public final class CDNClient extends PacketInboundHandler {
    private final MCUpdaterClient client;
    private final InetSocketAddress address;
    private final String repo;
    private final Map<String, String> downloadFileList;
    private final Consumer<Map<String, String>> callback;
    private Map<String, String> failed = null;

    public CDNClient(MCUpdaterClient client, InetSocketAddress address, String repo, UpdateHeader header, Consumer<Map<String, String>> callback) {
        this.client = client;
        this.address = address;
        this.repo = repo;
        this.downloadFileList = header.getServerDownloads();
        this.callback = callback;
    }

    public void run() {
        var group = new NioEventLoopGroup();
        var bootstrap = new Bootstrap();

        MCUProtocolV2.clientOptions(bootstrap);

        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class);

        bootstrap.handler(MCUProtocolV2.initializer().config((i) -> {
            i.handler(ClientFileReceiveHandler::new);
            i.handler(() -> this);
            i.handler(ErrorCatchHandler::new);
        }));

        try {
            var cf = bootstrap.connect(this.address).sync();
            cf.channel().closeFuture().sync();
        } catch (Exception e) {
            this.client.handleException(ClientError.NETWORK, e);
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        MCUProtocolV2.sendPacket(ctx, new P02_CDNHandShake(this.repo));
        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof P03_CDNHandShakeResponse) {
            MCUProtocolV2.sendPacket(ctx, new P30_FileDownloadRequest(this.downloadFileList));
        }
        if (packet instanceof P73_FTPCancel) {
            this.client.handleException(ClientError.NETWORK, new IllegalStateException("FTP protocol error!"));
            return;
        }

        if (packet instanceof P31_FileDownloadStart message) {
            this.failed = message.getFailedFiles();
        }

        if ((packet instanceof P33_FTPUpgradeToHttp message)) {
            var host = this.client.getServerMeta().getCdnHost();
            ServerUpgradedDownloadTask.start(message, host).whenComplete((a, b) -> ctx.writeAndFlush(new P34_UpgradeFileDownloadComplete()));
        }

        if (packet instanceof P32_FileDownloadComplete) {
            this.callback.accept(this.failed);
            ctx.disconnect();
        }
    }
}
