package org.atcraftmc.updater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import org.atcraftmc.updater.util.FilePath;
import org.atcgroup.mcupdater.PatchFile;
import org.atcraftmc.updater.client.Event;
import org.atcraftmc.updater.client.util.DeferredTaskManager;
import org.atcraftmc.updater.client.util.Log;
import org.atcraftmc.updater.network.packet.P10_VersionInfo;
import org.atcraftmc.updater.network.packet.P13_PatchFileInfo;
import org.atcraftmc.updater.network.packet.P14_PatchFileSlice;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PatchFileHandler extends MCUNetHandler {
    private final Map<Long, File> downloadedFiles = new HashMap<>();
    private RandomAccessFile target;
    private File file;
    private long writeLength;
    private long totalLength;
    private long currentFileCreated;
    private Consumer<File> receivedHandler = (f)->{};

    @Override
    public void handlePacket(Packet packet, ChannelHandlerContext ctx) throws Exception {
        if (packet instanceof P13_PatchFileInfo start) {
            Log.info("Receiving resource pack: " + start.getCreated());
            this.file = new File(FilePath.updater() + "/patches/" + UUID.randomUUID() + ".zip");
            if(!this.file.getParentFile().mkdirs()){
                Log.error("Could not create directory: " + this.file.getParent());
            }
            if(!this.file.createNewFile()){
                Log.error("Could not create file: " + this.file.getAbsolutePath());
            }
            this.target = new RandomAccessFile(this.file, "rw");
            this.writeLength = 0;
            this.currentFileCreated = start.getCreated();
            this.totalLength = start.getLength();
        }
        if (packet instanceof P14_PatchFileSlice slice) {
            var data = slice.getData();
            this.target.seek(this.writeLength);
            this.target.write(data);
            this.writeLength += data.length;

            var wm = this.writeLength / 1048576;
            var tm = this.totalLength / 1048576;
            var p = (int) ((float) this.writeLength / this.totalLength * 100);

            this.callEvent(Event.PROGRESS_WORKING, "正在下载更新资源包... %s/%sMB (%s)".formatted(wm, tm, p) + "%", wm, tm);

            if (slice.sig() == P14_PatchFileSlice.SIG_END && this.writeLength == this.totalLength) {
                this.target.close();

                this.downloadedFiles.put(this.currentFileCreated, this.file);
                this.currentFileCreated = Long.MAX_VALUE;
                this.receivedHandler.accept(this.file);

                this.target = null;
                this.file = null;
            }
        }

        if (packet instanceof P10_VersionInfo) {
            ctx.fireChannelRead(packet);
            finish();
        }
    }

    public void finish(){
        DeferredTaskManager.addFileTask(this::unzipFiles);
    }

    private void unzipFiles() {
        var count = 1;

        for (var id : downloadedFiles.keySet()) {
            var file = downloadedFiles.get(id);
            callEvent(Event.PROGRESS_WORKING, "正在解压资源包 (第%d/%d个)", count, this.downloadedFiles.size());

            int finalCount = count;
            PatchFile.unzip(file, FilePath.runtime(), (w, a) -> callEvent(
                    Event.PROGRESS_WORKING,
                    "正在解压资源包 (第%d/%d个) - 处理文件 %s/%s".formatted(finalCount, this.downloadedFiles.size(), w, a),
                    finalCount,
                    this.downloadedFiles.size()
            ));

            count++;
        }

        this.downloadedFiles.clear();
    }

    public void setReceivedCallback(Consumer<File> cb) {
        this.receivedHandler = cb;
    }
}
