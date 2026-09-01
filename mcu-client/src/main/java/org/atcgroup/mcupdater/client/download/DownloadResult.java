package org.atcgroup.mcupdater.client.download;

import me.gb2022.commons.file.FilePath;
import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.client.ClientFilePath;
import org.atcgroup.mcupdater.client.ui.TaskListener;
import org.atcgroup.mcupdater.client.ui.screen.ProcessScreen;
import org.atcgroup.mcupdater.util.FilePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class DownloadResult {
    private final Set<String> filesToExtract = new HashSet<>();
    private final Map<String, String> filesToUpdate = new ConcurrentHashMap<>();
    private final Set<Future<?>> tasksToWait = new HashSet<>();

    public void addExtractFile(String file) {
        this.filesToExtract.add(file);
    }

    public void addUpdateFile(String dest, String file) {
        this.filesToUpdate.put(dest, file);
    }

    public void addPendingTask(Future<?> task) {
        this.tasksToWait.add(task);
    }


    public void sync(TaskListener listener) {
        var total = this.tasksToWait.size();
        var counter = 0;

        for (var task : this.tasksToWait) {
            counter++;

            if (!((ProcessScreen) listener).isActive()) {
                listener.setProgress((int) (float) (counter / total) * 100);
                listener.setProgressTitle("正在等待下载任务完成(点击[后台任务]可查看): %s/%s".formatted(counter, total));
            }

            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Map<String, String> getFilesToUpdate() {
        return filesToUpdate;
    }

    public Set<String> getFilesToExtract() {
        return filesToExtract;
    }

    public void complete(TaskListener listener) {
        var counter = 1;
        for (var s : this.filesToExtract) {
            var file = ClientFilePath.CACHE.append(s).file();
            var a0 = this.filesToExtract.size();

            int finalCounter = counter;
            PatchFile.unzip(file, FilePaths.runtime(), (c, a) -> {
                var p = (int) (c / (float) a * 100);
                listener.setProgressTitle("正在解压资源包 第(%s/%s个) [%s/%s]".formatted(finalCounter, a0, c, a));
                listener.setProgress(p);
            });

            counter++;
        }

        counter = 1;

        var a0 = this.filesToUpdate.size();
        for (var s : this.filesToUpdate.keySet()) {
            var cache = ClientFilePath.CACHE.append(this.filesToUpdate.get(s)).file();
            var target = FilePath.RUNTIME.append(s).file();

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
}
