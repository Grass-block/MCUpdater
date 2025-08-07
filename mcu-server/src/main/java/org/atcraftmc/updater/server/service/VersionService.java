package org.atcraftmc.updater.server.service;

import com.google.gson.JsonParser;
import org.atcraftmc.updater.FilePath;
import org.atcraftmc.updater.PatchFile;
import org.atcraftmc.updater.data.VersionInfo;
import org.atcraftmc.updater.server.MCUpdaterServer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.atcraftmc.updater.server.MCUpdaterServer.LOGGER;

public final class VersionService extends Service {
    private final Map<String, Map<String, VersionInfo>> versions = new HashMap<>();
    private final FileService fileService;

    public VersionService(MCUpdaterServer server, FileService fileService) {
        super(server);
        this.fileService = fileService;
    }

    @Override
    public String name() {
        return "version-service";
    }

    @Override
    public void run() {
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
        this.channel(info.channel()).put(info.version(), info);
    }

    public Map<String, VersionInfo> channel(String channel) {
        return this.versions.computeIfAbsent(channel, k -> new HashMap<>());
    }

    public void registerVersion(VersionInfo info) {
        this.addVersion(info);
        var file = new File(FilePath.runtime() + "/versions/" + info.channel() + "/" + info.version() + ".json");

        if (file.getParentFile().mkdirs()) {
            LOGGER.info("created directory {}", file.getParentFile().getAbsolutePath());
        }

        try (var o = new FileOutputStream(file)) {
            o.write(info.json().toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (Objects.equals(info.version(), "__install")) {
            return;
        }

        var logFile = new File(FilePath.runtime() + "/versions/" + info.channel() + "/" + info.version() + ".txt");

        logFile.getParentFile().mkdirs();
        try {
            logFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("正在更新安装信息...");

        registerVersion(createInstall(info.channel()));

        LOGGER.info("版本 {}-{} 注册成功, 请在 {} 填写更新日志。", info.channel(), info.version(), logFile.getAbsolutePath());
    }

    public List<VersionInfo> sorted(String channel) {
        return channel(channel).values()
                .stream()
                .filter((v) -> !Objects.equals(v.version(), "__install"))
                .sorted(Comparator.comparingLong(VersionInfo::timestamp))
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
        var paths = server().fileService().sources().get(channel).paths();
        var zipFile = new File(FilePath.resourcePack(channel, "__install"));
        var pid = FilePath.resourcePackId(channel, "__install");

        var files = new HashMap<String, File>();

        for (var path : paths) {
            var file = this.fileService.sources().get(channel).file(path);
            files.put(path, file);
        }

        LOGGER.info("正在更新安装资源包...");

        PatchFile.zip(zipFile, files);
        //this.server().cdn.planUpload(channel + "_" + "__install");
        return new VersionInfo(channel, "__install", System.currentTimeMillis(), new HashSet<>(), new HashSet<>(), Set.of(pid));
    }

    public VersionInfo install(String channel) {
        var latest = latest(channel);
        var install = channel(channel).get("__install");

        if (latest == null) {
            return new VersionInfo(
                    channel,
                    "初始安装版本",
                    install.timestamp(),
                    install.update(),
                    install.remove(),
                    install.resourcePack()
            );
        }

        return new VersionInfo(
                latest.channel(),
                latest.version(),
                latest.timestamp(),
                install.update(),
                install.remove(),
                install.resourcePack()
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
            if (timestamp < install.timestamp()) {
                return install;
            }

            return null;
        }

        if (data.get(0).timestamp() > timestamp) {
            return install;
        }

        data.removeIf((v) -> v.timestamp() <= timestamp);

        if (data.isEmpty()) {
            return null;
        }


        return VersionInfo.ofMerged(data);
    }

    public List<VersionInfo> all() {
        var result = new ArrayList<VersionInfo>();

        for (var map: this.versions.values()){
            result.addAll(map.values());
        }

        return result;
    }
}
