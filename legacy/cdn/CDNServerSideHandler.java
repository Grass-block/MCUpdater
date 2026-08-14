package org.atcraftmc.mcupdater.cdn.legacy;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.packet.PacketInboundHandler;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.util.FileChecksumManager;
import org.atcgroup.mcupdater.util.Async;
import org.atcgroup.mcupdater.util.FileLockManager;
import org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer;
import org.atcraftmc.updater.network.packet.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.atcraftmc.mcupdater.cdn.MCUpdaterCDNServer.LOGGER;

public final class CDNServerSideHandler extends PacketInboundHandler {
    private final Set<String> requests = new HashSet<>();
    private String repo;
    private String lastFile;
    private long lastWrite;
    private RandomAccessFile raFile;
    private File file;

    private boolean isConnectionInvalid(String repo, String token) {
        var props = MCUpdaterCDNServer.INSTANCE.config();
        if (!props.containsKey(repo) || !Objects.equals(props.getProperty(repo), token)) {
            return true;
        }

        if (this.repo == null) {
            this.repo = repo;
            return false;
        }

        return !this.repo.equals(repo);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        //收到文件上传预定，尝试等待连接的客户端完成下载后且没有正在上传的情况下上锁对应文件以供写入
        if (packet instanceof P54_CDNFileChangeAttempt attempt) {
            var repo = attempt.getRepo();
            var token = attempt.getToken();

            if (isConnectionInvalid(repo, token)) {
                ctx.disconnect();
                return;
            }

            Async.WORKER.submit(() -> {
                while (!requests.isEmpty()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    Thread.yield();
                }

                for (var s : attempt.getTargets()) {
                    var file = FileChecksumManager.getTargetFile(repo, s);
                    FileLockManager.tryLockWrite(file);
                }

                MCUProtocolV2.sendPacket(ctx, new P55_CDNFileChangeReady(attempt.getRepo(), attempt.getToken(), attempt.getTargets()));

                this.requests.addAll(attempt.getTargets());
            });
            return;
        }

        //如果还在写入那就不允许从CDN下载
        if (packet instanceof P50_CDNCheckObjectStatus status) {
            var owner = status.getOwner();
            var token = status.getToken();
            var name = status.getName();

            if (isConnectionInvalid(owner, token)) {
                ctx.disconnect();
            }

            if (FileLockManager.isWriting(FileChecksumManager.getTargetFile(owner, name))) {
                LOGGER.info("收到文件状态请求({}), 结果: WRITING", name);
                MCUProtocolV2.sendPacket(ctx, new P51_CDNObjectState(owner, name, status.getQueryId(), ""));
                return;
            }

            LOGGER.info("收到文件状态请求({}), 结果: {}", name, FileChecksumManager.getFileStatus(owner, name));
            MCUProtocolV2.sendPacket(ctx, new P51_CDNObjectState(owner, name, status.getQueryId(), FileChecksumManager.getFileStatus(owner, name)));
            return;
        }

        //receive upload
        if (packet instanceof P52_CDNUploadObject upload) {
            var repo = upload.getOwner();
            var token = upload.getAccessToken();
            var name = upload.getName();

            if (isConnectionInvalid(repo, token)) {
                ctx.disconnect();
            }

            if (!Objects.equals(name, this.lastFile) || this.lastFile == null) {
                if (this.raFile != null) {
                    this.raFile.close();
                }

                this.file = FileChecksumManager.getTargetFile(repo, name);

                this.file.getParentFile().mkdirs();
                this.file.createNewFile();

                this.lastWrite = 0;
                this.lastFile = upload.getName();
                this.raFile = new RandomAccessFile(this.file, "rw");
            }

            this.raFile.seek(this.lastWrite);
            this.raFile.write(upload.getData());
            this.lastWrite += upload.getData().length;

            if (!upload.hasNext()) {
                this.raFile.close();
                this.requests.remove(upload.getName());
                FileLockManager.unlockWrite(this.file);
                FileChecksumManager.updateFileStatus(repo, name);
                LOGGER.info("成功上传: {}-{}bytes", this.file, this.lastWrite);
            }

            return;
        }

        ctx.fireChannelRead(packet);
    }
}
