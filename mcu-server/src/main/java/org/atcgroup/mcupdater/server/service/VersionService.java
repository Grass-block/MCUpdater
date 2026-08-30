package org.atcgroup.mcupdater.server.service;

import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.data.VersionSet;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.server.data.FileModifyStatus;
import org.atcgroup.mcupdater.server.data.FileSource;
import org.atcgroup.mcupdater.server.file.FileAddHandler;
import org.atcgroup.mcupdater.util.FilePath;
import org.atcgroup.mcupdater.util.I18n;
import org.bukkit.configuration.ConfigurationSection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.atcgroup.mcupdater.server.MCUpdaterServer.LOGGER;

public final class VersionService implements Service {
    private final Map<String, FileSource> sources = new HashMap<>();
    private final Map<String, Map<String, VersionInfo>> versions = new HashMap<>();

    @Override
    public void handleBootstrap(ConfigurationSection config) {
        LOGGER.info(I18n.message("version.source.loading"));
        FileAddHandler.init(config.getConfigurationSection("file-analyzer"));

        var section = config.getConfigurationSection("channels");

        if (section == null) {
            throw new IllegalStateException("No channels section found in config :(");
        }

        var ids = section.getKeys(false);

        for (var s : ids) {
            var source = new FileSource(s, section.getConfigurationSection(s));
            var meta = source.meta();

            this.sources.put(s, source);

            LOGGER.info(I18n.message("version.source.entry", meta.id(), meta.name(), meta.required(), source.path()));
        }

        LOGGER.info(I18n.message("version.channels.loaded", ids.size()));


        this.versions.clear();

        for (var id : this.sources.keySet()) {
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
                        addVersion(VersionInfo.fromJson(JsonParser.parseReader(r).getAsJsonObject()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            LOGGER.info(I18n.message("version.source.versions_loaded", id, list.size()));

            if (!channel(id).containsKey("__install")) {
                LOGGER.info(I18n.message("version.install.generating", id));
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
            LOGGER.info(I18n.message("version.directory_created", file.getParentFile().getAbsolutePath()));
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

        LOGGER.info(I18n.message("version.install.updating"));

        registerVersion(createInstall(info.getChannel()));

        LOGGER.info(I18n.message("version.registered", info.getChannel(), info.getVersion(), logFile.getAbsolutePath()));
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
        var paths = this.sources.get(channel).paths();
        var files = new HashMap<String, File>();

        for (var path : paths) {
            var file = this.sources.get(channel).file(path);
            files.put(path, file);
        }

        LOGGER.info(I18n.message("version.install.resource_pack"));

        var vi = new VersionInfo(channel, System.currentTimeMillis(), "__install", new HashSet<>(), new HashMap<>());

        FileAddHandler.iterateFiles(files, vi);

        return vi;
    }


    public VersionInfo install(String channel) {
        var latest = latest(channel);
        var install = channel(channel).get("__install");

        if (latest == null) {
            return new VersionInfo(channel,
                                   install.getTimestamp(),
                                   "[initial]",
                                   install.getDeleteFileList(),
                                   install.getResourcePackList(),
                                   install.getUpdateFileList(),
                                   install.getDownloadFileList()
            );
        }

        return new VersionInfo(latest.getChannel(),
                               latest.getTimestamp(),
                               latest.getVersion(),
                               install.getDeleteFileList(),
                               install.getResourcePackList(),
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

    public VersionInfo from(String channel, long timestamp, VersionSet container) {
        var data = sorted(channel);
        var install = install(channel);

        if (data.isEmpty()) {
            if (timestamp < install.getTimestamp()) {
                container.addVersion(install.getChannel(), install.getVersion());
                return install;
            }

            return null;
        }

        if (data.get(0).getTimestamp() > timestamp) {
            container.addVersion(install.getChannel(), install.getVersion());
            return install;
        }

        data.removeIf((v) -> v.getTimestamp() <= timestamp);

        if (data.isEmpty()) {
            return null;
        }

        for (var v : data) {
            container.addVersion(v.getChannel(), v.getVersion());
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

    public void buildVersion(String channel, String version) {
        if (!this.sources.containsKey(channel)) {
            LOGGER.error("更新频道 {} 不存在。", channel);
            return;
        }

        if (this.channel(channel).containsKey(version)) {
            LOGGER.error("版本号 {} 已经存在。", version);
            return;
        }

        LOGGER.info("正在检查文件状态...");
        var source = this.sources.get(channel);
        var status = source.fileManager().check();

        if (status.values().stream().allMatch((s) -> s == FileModifyStatus.NONE)) {
            LOGGER.error("没有文件更改，生成撤销。");
            return;
        }

        var update = new HashSet<String>();
        var remove = new HashSet<String>();

        status.remove("");

        for (var path : status.keySet()) {
            var state = status.get(path);
            if (state == FileModifyStatus.ADD) {
                update.add(path);
                LOGGER.info("[A] {}", path);
            }
            if (state == FileModifyStatus.DELETE) {
                remove.add(path);
                LOGGER.info("[D] {}", path);
            }
            if (state == FileModifyStatus.UPDATE) {
                update.add(path);
                LOGGER.info("[E] {}", path);
            }
        }

        var files = new HashMap<String, File>();
        for (var path : update) {
            var file = this.sources.get(channel).file(path);
            files.put(path, file);
        }

        var time = System.currentTimeMillis();
        var vi = new VersionInfo(channel, time, version, remove, new HashMap<>());

        FileAddHandler.iterateFiles(files, vi);

        this.registerVersion(vi);
        LOGGER.info("版本已创建: {}-{} 打包时间: {}", channel, version, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time));
    }

    @Override
    public boolean handleCommand(String command, String[] args) {
        if (Objects.equals(command, "build")) {
            buildVersion(args[0], args[1]);
            return true;
        }

        return false;
    }

    @Override
    public void handleNetworkMessage(Packet packet, ChannelHandlerContext ctx) {
        if (packet instanceof P10_ChannelHeaderRequest) {
            var data = this.sources.values().stream().map(FileSource::meta).collect(Collectors.toSet());

            MCUProtocolV2.sendPacket(ctx, new P20_ChannelHeaders(data));
            return;
        }

        if ((packet instanceof P11_UpdateRequest request)) {
            var sum = getService(ResourcePackService.class).getChecksumManager();
            var vs = new VersionSet();

            var versions = request.getCurrentVersions().entrySet().stream().map((e) -> this.from(e.getKey(), e.getValue(), vs)).collect(
                    Collectors.toSet());

            versions.removeIf(Objects::isNull);

            MCUProtocolV2.sendPacket(ctx, new P21_VersionHeaders(versions, vs, sum));
            return;
        }

        if ((packet instanceof P12_UpdateLogRequest request)) {
            var versions = request.getVersions();
            var result = new P22_UpdateLogs();

            for (var channel : versions.keySet()) {
                for (var version : versions.get(channel)) {
                    result.addLog(channel, version, log(channel, version));
                }
            }

            for (var ch : versions.keySet()) {
                result.addChannelDisplayName(ch, this.sources.get(ch).meta().name());
            }

            MCUProtocolV2.sendPacket(ctx, result);
        }
    }
}
