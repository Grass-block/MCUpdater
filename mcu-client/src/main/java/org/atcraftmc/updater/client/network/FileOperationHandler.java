package org.atcraftmc.updater.client.network;

import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import org.atcraftmc.updater.util.FilePath;
import org.atcraftmc.updater.client.util.DeferredTaskManager;
import org.atcraftmc.updater.client.Event;
import org.atcraftmc.updater.network.packet.P11_FileExpand;
import org.atcraftmc.updater.network.packet.P12_FileDelete;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileOperationHandler extends MCUNetHandler {

    @Override
    public void handlePacket(Packet packet, ChannelHandlerContext ctx) {
        if (packet instanceof P11_FileExpand p) {
            runTask(() -> handleFileExpand(p));
            return;
        }
        if (packet instanceof P12_FileDelete p) {
            DeferredTaskManager.deleteFileTask(() -> handleFileDelete(p));
        }
    }

    private void handleFileDelete(P12_FileDelete p) {
        for (var path : p.getPaths()) {
            var file = new File(FilePath.runtime() + path);
            var dest = new File(FilePath.runtime() + "/.updater/removed/" + path);

            if (!file.exists()) {
                continue;
            }

            try {
                dest.getParentFile().mkdirs();
                dest.createNewFile();

                Files.move(Path.of(file.getAbsolutePath()), Path.of(dest.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handleFileExpand(P11_FileExpand p) {
        try {
            for (var entry : p.getList().entrySet()) {
                var path = entry.getKey();
                var data = entry.getValue();

                callEvent(Event.PROGRESS, "正在复制文件 - %s".formatted(path));

                var file = new File(FilePath.runtime() + path);

                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }

                try (var out = new FileOutputStream(file)) {
                    out.write(data);
                    out.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
