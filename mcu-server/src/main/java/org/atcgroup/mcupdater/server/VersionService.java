package org.atcgroup.mcupdater.server;

import com.google.gson.JsonParser;
import org.atcgroup.mcupdater.PatchFile;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.server.file.FileService;
import org.atcraftmc.updater.util.FilePath;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.atcraftmc.updater.server.MCUpdaterServer.LOGGER;

public final class VersionService {
    private final Map<String, Map<String, VersionInfo>> versions = new HashMap<>();
    private final FileService fileService;

    public VersionService(FileService fileService) {
        this.fileService = fileService;
    }

    public void start() {
        this.versions.clear();

        for (var id : this.fileService.sources().keySet()) {
            var list = channel(id);
            var folder = new File(FilePath.runtime() + "/versions/" + id);

            list.clear();

            var data = folder.listFiles();

            if (data != null) {
                for (var f : data) {
                    if (!f.getName().endsWith(".json") || f.isDirectory()) {
                        continue;
                    }

                    try (var in = new FileInputStream(f); var r = new InputStreamReader(in)) {
                        addVersion(new VersionInfo(JsonParser.parseReader(r).getAsJsonObject()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            LOGGER.info("数据源 {} 加载了 {} 个版本。", id, list.size());

            if (!channel(id).containsKey("__install")) {
                LOGGER.info("正在生成 {} 的默认安装信息...", id);
                registerVersion(createInstall(id));
            }
        }
    }

    public void addVersion(VersionInfo info) {
        this.channel(info.getChannel()).put(info.getVersion(), info);
    }

    public Map<String, VersionInfo> channel(String channel) {
        return this.versions.computeIfAbsent(channel, k -> new HashMap<>());
    }

    public void registerVersion(VersionInfo info) {
        this.addVersion(info);
        var file = new File(FilePath.runtime() + "/versions/" + info.getChannel() + "/" + info.getVersion() + ".json");

        if (file.getParentFile().mkdirs()) {
            LOGGER.info("created directory {}", file.getParentFile().getAbsolutePath());
        }

        try (var o = new FileOutputStream(file)) {
            o.write(info.json().toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (Objects.equals(info.getVersion(), "__install")) {
            return;
        }

        var logFile = new File(FilePath.runtime() + "/versions/" + info.getChannel() + "/" + info.getVersion() + ".txt");

        logFile.getParentFile().mkdirs();
        try {
            logFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("正在更新安装信息...");

        registerVersion(createInstall(info.getChannel()));

        LOGGER.info("版本 {}-{} 注册成功, 请在 {} 填写更新日志。", info.getChannel(), info.getVersion(), logFile.getAbsolutePath());
    }

    public List<VersionInfo> sorted(String channel) {
        return channel(channel).values()
                .stream()
                .filter((v) -> !Objects.equals(v.getVersion(), "__install"))
                .sorted(Comparator.comparingLong(VersionInfo::getTimestamp))
                .collect(Collectors.toList());
    }

    public VersionInfo latest(String channel) {
        var data = sorted(channel);

        if (data.isEmpty()) {
            return null;
        }

        return data.get(data.size() - 1);
    }

    public VersionInfo createInstall(String channel) {
        var paths = MCUpdaterServer.instance().getFileService().sources().get(channel).paths();
        var zipFile = new File(FilePath.resourcePack(channel, "__install"));
        var pid = FilePath.resourcePackId(channel, "__install");

        var files = new HashMap<String, File>();

        for (var path : paths) {
            var file = this.fileService.sources().get(channel).file(path);
            files.put(path, file);
        }

        LOGGER.info("正在更新安装资源包...");

        PatchFile.zip(zipFile, files);

        return new VersionInfo(
                channel,
                System.currentTimeMillis(),
                "__install",
                new HashSet<>(),
                Set.of(pid),
                new HashMap<>(),
                new HashMap<>()
        );
    }

    public VersionInfo install(String channel) {
        var latest = latest(channel);
        var install = channel(channel).get("__install");

        if (latest == null) {
            return new VersionInfo(
                    channel,
                    install.getTimestamp(),
                    "[initial]",
                    install.getDeleteFileList(),
                    install.getDownloadPackList(),
                    install.getUpdateFileList(),
                    install.getDownloadFileList()
            );
        }

        return new VersionInfo(
                latest.getChannel(),
                latest.getTimestamp(),
                latest.getVersion(),
                install.getDeleteFileList(),
                install.getDownloadPackList(),
                install.getUpdateFileList(),
                install.getDownloadFileList()
        );
    }

    public String log(String channel, String version) {
        var file = new File(FilePath.runtime() + "/versions/" + channel + "/" + version + ".txt");

        if (!file.exists() || file.length() == 0) {
            return "暂无更新信息 :(";
        }

        try (var in = new FileInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public VersionInfo from(String channel, long timestamp) {
        var data = sorted(channel);
        var install = install(channel);

        if (data.isEmpty()) {
            if (timestamp < install.getTimestamp()) {
                return install;
            }

            return null;
        }

        if (data.get(0).getTimestamp() > timestamp) {
            return install;
        }

        data.removeIf((v) -> v.getTimestamp() <= timestamp);

        if (data.isEmpty()) {
            return null;
        }


        return VersionInfo.ofMerged(data);
    }

    public List<VersionInfo> all() {
        var result = new ArrayList<VersionInfo>();

        for (var map : this.versions.values()) {
            result.addAll(map.values());
        }

        return result;
    }
}
