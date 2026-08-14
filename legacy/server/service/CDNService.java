package org.atcraftmc.updater.server.service;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcraftmc.updater.util.FilePath;
import org.atcraftmc.updater.network.CDNClient;
import org.atcraftmc.updater.network.packet.P50_CDNCheckObjectStatus;
import org.atcraftmc.updater.network.packet.P54_CDNFileChangeAttempt;
import org.atcraftmc.updater.network.packet.P55_CDNFileChangeReady;
import org.atcraftmc.updater.server.MCUpdaterServer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.atcraftmc.updater.server.MCUpdaterServer.LOGGER;

public final class CDNService extends Service {
    private final CDNClient client;
    private final boolean enable;
    private final String repository;
    private final String token;
    private final Set<String> uploaded = new HashSet<>();
    private final NetHandler netHandler = new NetHandler();

    public CDNService(MCUpdaterServer server) {
        super(server);
        this.enable = server.config().getBoolean("cdn-server.enable", false);
        this.repository = server.config().getString("cdn-server.repository", "/mcu-example");
        this.token = server.config().getString("cdn-server.access-token", "");
        if (this.enable) {
            var ip = server.config().getString("cdn-server.address");
            var port = server.config().getInt("cdn-server.port");
            LOGGER.info("正在尝试连接到CDN服务器 {}:{}", ip, port);
            this.client = new CDNClient(ip, port).addHandler(() -> this.netHandler);
        } else {
            this.client = null;
        }
    }

    @Override
    public String name() {
        return "cdn-service";
    }

    @Override
    public void run() {
        this.client.run();
    }

    public String getRepository() {
        return this.repository;
    }

    public String getCDNFileStatus(String file) {
        if (!this.enable) {
            return "";
        }

        LOGGER.info("正在查询CDN文件状态: {}", file);

        var request = new P50_CDNCheckObjectStatus(UUID.randomUUID().toString(), this.repository, file, this.token);

        try {
            return this.client.sendQuery(request, org.atcraftmc.updater.network.packet.P51_CDNObjectState.class).get().getChecksum();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getToken() {
        return token;
    }

    public CDNClient getClient() {
        return client;
    }

    @Override
    public boolean async() {
        return false;
    }

    public void planUpload(String pack) {
        if (!this.enable) {
            return;
        }
        LOGGER.info("已注册CDN上传任务: {}", pack);
        this.uploaded.add(pack);
    }

    public void batchUpload() {
        LOGGER.info("开始上传任务 (共{}个资源)", this.uploaded.size());
        this.client.send(new P54_CDNFileChangeAttempt(this.repository, this.token, this.uploaded));
    }

    public boolean isUploading() {
        return !this.uploaded.isEmpty();
    }

    @ChannelHandler.Sharable
    private final class NetHandler extends PacketInboundHandler {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
            if (packet instanceof P55_CDNFileChangeReady r) {
                LOGGER.info("CDN服务器准备完成，开始上传数据...");

                new Thread(() -> {
                    for (var f : r.getTargets()) {
                        var file = new File(FilePath.runtime() + "/packs/" + f + ".zip");
                        org.atcraftmc.updater.network.packet.P52_CDNUploadObject.read(file, repository, token, (p)->{
                            try {
                                MCUProtocolV2.sendPacket(ctx, p).get();
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        LOGGER.info("成功上传文件 {}", file.getAbsolutePath());
                        uploaded.remove(f);
                    }
                }).start();
                return;
            }

            ctx.fireChannelRead(packet);
        }
    }
}
