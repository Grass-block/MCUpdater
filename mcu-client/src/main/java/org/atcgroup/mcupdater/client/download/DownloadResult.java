package org.atcgroup.mcupdater.client.download;

import me.gb2022.commons.file.FilePath;
import me.gb2022.mcupdater.client.ui.desktop.screen.ProcessScreen;
import me.gb2022.mcupdater.client.util.ClientFilePath;
import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.client.ui.TaskListener;
import org.atcgroup.mcupdater.util.FilePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadResult {
    private final Set<Future<?>> tasksToWait = new HashSet<>();
    private int waitingCounter = 0;
    private TaskListener listener = null;
    private long lastUpdateTime;





    private final UpdateHeader header;

    public DownloadResult(UpdateHeader header) {
        this.header = header;
    }

    public void addPendingTask(Future<?> task) {
        this.tasksToWait.add(task);
    }

    public void sync(TaskListener listener) {
        this.listener = listener;

        var active = new AtomicBoolean(true);
        var trackerThread = new Thread(() -> {
            while (active.get()) {
                var now = System.currentTimeMillis();
                var elapsed = now - this.lastUpdateTime;
                this.lastUpdateTime = now;

                var total = this.tasksToWait.size();

                if (!(this.listener instanceof ProcessScreen ps)) {
                    continue;
                }

                if (!ps.isActive()) {
                    var spd = (ps.checkAllWorkDiff() / (elapsed / 1000f)) / 1048576f;

                    this.listener.setProgress((int) ((float) this.waitingCounter / total * 100));
                    this.listener.setProgressTitle("正在等待下载任务完成(点击[后台任务]可查看): %s/%s [总速度: %.2f MB/s]".formatted(this.waitingCounter, total, spd));
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "DownloadTaskTracker");

        trackerThread.start();

        for (var task : this.tasksToWait) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        active.set(false);
        trackerThread.interrupt();
    }

    public void complete(TaskListener listener) {
        var counter = 1;
        var delete = this.header.getDeleteFiles();
        for (var s : delete) {
            var a = delete.size();
            var p = (int) (counter / (float) a * 100);

            var file = FilePath.RUNTIME.append(s).file();

            if(!file.exists()){
                return;
            }

            listener.setUnsureProgress("正在移除旧版文件 [%s/%s - %s%%] : %s".formatted(counter, a, p, s));

            counter++;
        }

        counter = 1;
        var extract = this.header.getExpandFiles();
        for (var s : extract) {
            var file = ClientFilePath.CACHE.append(s).file();
            var a0 = extract.size();

            int finalCounter = counter;
            PatchFile.unzip(file, FilePaths.runtime(), (c, a) -> {
                var p = (int) (c / (float) a * 100);
                listener.setProgressTitle("正在解压资源包 第(%s/%s个) [%s/%s]".formatted(finalCounter, a0, c, a));
                listener.setProgress(p);
            });

            counter++;
        }

        counter = 1;

        var a0 = this.header.getReplaceFiles().size();
        var update = this.header.getReplaceFiles();
        for (var s : update.keySet()) {
            var cache = ClientFilePath.CACHE.append(s).file();
            var target = FilePath.RUNTIME.append(update.get(s)).file();

            target.getParentFile().mkdirs();
            try {
                target.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                var p = (int) (float) (a0 / counter * 100);
                listener.setProgressTitle("正在替换独立文件 第(%s/%s个)".formatted(counter, a0));
                listener.setProgress(p);
                Files.copy(cache.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            counter++;
        }
    }

    public void pendingTaskComplete() {
        this.waitingCounter++;
    }
}
