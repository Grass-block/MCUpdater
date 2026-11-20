package org.atcgroup.mcupdater.server.service;

import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import me.gb2022.simpnet.packet.Packet;
import org.atcgroup.mcupdater.CompiledVersionData;
import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.atcgroup.mcupdater.network.MCUProtocolV2;
import org.atcgroup.mcupdater.network.packet.*;
import org.atcgroup.mcupdater.server.MCUpdaterServer;
import org.atcgroup.mcupdater.server.VersionPostCompileHandler;
import org.atcgroup.mcupdater.server.analyzer.CurseForgeModAnalyzer;
import org.atcgroup.mcupdater.server.analyzer.ModrinthModAnalyzer;
import org.atcgroup.mcupdater.server.analyzer.PackCompressHandler;
import org.atcgroup.mcupdater.server.analyzer.VersionFileAnalyzer;
import org.atcgroup.mcupdater.server.file.FileModifyStatus;
import org.atcgroup.mcupdater.server.file.FileSource;
import org.atcgroup.mcupdater.util.FileDigest;
import org.atcgroup.mcupdater.util.FilePaths;
import org.atcgroup.mcupdater.util.I18n;
import org.atcgroup.mcupdater.util.Pipeline;
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

    private final Pipeline<VersionPostCompileHandler> compiledVersionHandlerPipeline = new Pipeline<>();
    private final Pipeline<VersionFileAnalyzer> fileAnalyzerPipeline = new Pipeline<>();

    private final FileRepository resourceRepository;

    public VersionService(FileRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Override
    public void handleServerBootstrap(MCUpdaterServer server, ConfigurationSection config) {
        LOGGER.info(I18n.message("version.source.loading"));
        initAnalyzers(config.getConfigurationSection("file-analyzer"));
        loadVersions(config);
    }

    private void loadVersions(ConfigurationSection config) {
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
            var list = getVersionsByChannel(id);
            var folder = new File(FilePaths.runtime() + "/versions/" + id);

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

            if (!getVersionsByChannel(id).containsKey("__install")) {
                LOGGER.info(I18n.message("version.install.generating", id));
                registerVersion(buildInstallVersion(id));
            }
        }
    }

    private void initAnalyzers(ConfigurationSection config) {
        for (var id : config.getStringList("order")) {
            var dom = config.getConfigurationSection(id);

            this.fileAnalyzerPipeline.addLast(id, switch (id) {
                case "modrinth" -> new ModrinthModAnalyzer(dom);
                case "fallback" -> new PackCompressHandler(dom);
                case "curseforge" -> new CurseForgeModAnalyzer(dom);
                default -> throw new IllegalStateException("Unexpected FileAnalyzer ID: " + id);
            });
        }
    }


    public Pipeline<VersionFileAnalyzer> getFileAnalyzerPipeline() {
        return fileAnalyzerPipeline;
    }

    public Pipeline<VersionPostCompileHandler> getCompiledVersionHandlerPipeline() {
        return compiledVersionHandlerPipeline;
    }


    public void addVersion(VersionInfo info) {
        this.getVersionsByChannel(info.getChannel()).put(info.getVersion(), info);
    }

    public Map<String, VersionInfo> getVersionsByChannel(String channel) {
        return this.versions.computeIfAbsent(channel, k -> new HashMap<>());
    }

    public Map<String, FileSource> getSources() {
        return this.sources;
    }

    public List<VersionInfo> sorted(String channel) {
        return getVersionsByChannel(channel).values()
                .stream()
                .filter((v) -> !Objects.equals(v.getVersion(), "__install"))
                .sorted(Comparator.comparingLong(VersionInfo::getTimestamp))
                .collect(Collectors.toList());
    }

    public String getVersionLog(String channel, String version) {
        var file = new File(FilePaths.runtime() + "/versions/" + channel + "/" + version + ".txt");

        if (!file.exists() || file.length() == 0) {
            return "暂无更新信息 :(";
        }

        try (var in = new FileInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<VersionInfo> from(String channel, long timestamp) {
        var data = sorted(channel);
        var install = createInstallVersion(channel);
        var container = new ArrayList<VersionInfo>();

        if (data.isEmpty()) {
            if (timestamp < install.getTimestamp()) {
                container.add(install);
                return container;
            }

            return container;
        }

        if (data.getFirst().getTimestamp() > timestamp) {
            container.add(install);
            return container;
        }

        data.removeIf((v) -> v.getTimestamp() <= timestamp);

        if (data.isEmpty()) {
            return container;
        }

        container.addAll(data);

        return container;
    }

    public UpdateHeader compile(Map<String, Long> currentVersions) {
        var result = new UpdateHeader();

        for (var cv : currentVersions.entrySet()) {
            var versions = from(cv.getKey(), cv.getValue());
            var composed = composeVersionData(versions);

            if (composed != null) {
                result.addVersionData(composed);
            }

            for (var v : versions) {
                result.getDeleteFiles().addAll(v.getDeleteFileList());
                result.getExpandFiles().addAll(v.getResourcePackList());
                result.getReplaceFiles().putAll(v.getUpdateFileList());

                result.getExternDownloads().addAll(v.getDownloadFileList());
                for (var f : v.getResourcePackList()) {
                    var sha = this.resourceRepository.getFileInfo(f).getSha256();
                    result.addServerDownload(f, sha);
                }
                for (var f : v.getUpdateFileList().keySet()) {
                    var fi = this.resourceRepository.getFileInfo(f);

                    if (fi == null) {
                        continue;
                    }

                    result.addServerDownload(f, fi.getSha256());
                }
            }
        }

        for (var handler : this.compiledVersionHandlerPipeline.list()) {
            handler.handle(result, this.resourceRepository);
        }

        return result;
    }

    public void buildVersion(String channel, String version) {
        if (!this.sources.containsKey(channel)) {
            LOGGER.error("更新频道 {} 不存在。", channel);
            return;
        }

        if (this.getVersionsByChannel(channel).containsKey(version)) {
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

        analyzeVersionFiles(vi, files);

        this.registerVersion(vi);
        LOGGER.info("版本已创建: {}-{} 打包时间: {}", channel, version, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time));
    }


    private VersionInfo buildInstallVersion(String channel) {
        var paths = this.sources.get(channel).paths();
        var files = new HashMap<String, File>();

        for (var path : paths) {
            var file = this.sources.get(channel).file(path);
            files.put(path, file);
        }

        LOGGER.info(I18n.message("version.install.resource_pack"));

        var vi = new VersionInfo(channel, System.currentTimeMillis(), "__install", new HashSet<>(), new HashMap<>());

        analyzeVersionFiles(vi, files);

        return vi;
    }

    private VersionInfo createInstallVersion(String channel) {
        var data = sorted(channel);
        var latest = (VersionInfo) null;

        if (!data.isEmpty()) {
            latest = data.getLast();
        }

        var install = getVersionsByChannel(channel).get("__install");

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

    private void registerVersion(VersionInfo info) {
        this.addVersion(info);
        var file = new File(FilePaths.runtime() + "/versions/" + info.getChannel() + "/" + info.getVersion() + ".json");

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

        var logFile = new File(FilePaths.runtime() + "/versions/" + info.getChannel() + "/" + info.getVersion() + ".txt");

        logFile.getParentFile().mkdirs();
        try {
            logFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info(I18n.message("version.install.updating"));

        registerVersion(buildInstallVersion(info.getChannel()));

        LOGGER.info(I18n.message("version.registered", info.getChannel(), info.getVersion(), logFile.getAbsolutePath()));
    }

    private void analyzeVersionFiles(VersionInfo version, Map<String, File> files) {
        var data = new HashSet<VersionFileAnalyzer.SourceFileInfo>();

        for (var path : files.keySet()) {
            var file = files.get(path);
            var sha1 = FileDigest.calculateSHA1(file);

            data.add(new VersionFileAnalyzer.SourceFileInfo(path, file, sha1));
        }

        for (var handler : this.fileAnalyzerPipeline.list()) {
            handler.handleFiles(data, version);
        }
    }

    private CompiledVersionData composeVersionData(List<VersionInfo> versions) {
        versions.sort(Comparator.comparingLong(VersionInfo::getTimestamp));

        if (versions.isEmpty()) {
            return null;
        }

        var latest = versions.getLast();


        var channel = latest.getChannel();
        var channelName = this.sources.get(channel).meta().name();
        var headTimeStamp = latest.getTimestamp();

        var result = new CompiledVersionData(channel, channelName, headTimeStamp);

        for (var version : versions) {
            result.addElapsedUpdateLog(version.getVersion(), getVersionLog(channel, version.getVersion()));
        }

        return result;
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
            MCUpdaterServer.instance().getExecutor().submit(() -> {
                var data = compile(request.getCurrentVersions());
                MCUProtocolV2.sendPacket(ctx, new P21_VersionHeaders(data));
            });

            /*
            var sum = this.resourceRepository;
            var vs = new VersionSet();
            var versions = request.getCurrentVersions().entrySet().stream().map((e) -> this.from(e.getKey(), e.getValue(), vs)).collect(
                    Collectors.toSet());
            versions.removeIf(Objects::isNull);
            MCUProtocolV2.sendPacket(ctx, new P21_VersionHeaders(versions, vs, sum));
            return;
            */
            return;
        }

        if ((packet instanceof P12_UpdateLogRequest request)) {
            var versions = request.getVersions();
            var result = new P22_UpdateLogs();

            for (var channel : versions.keySet()) {
                for (var version : versions.get(channel)) {
                    result.addLog(channel, version, getVersionLog(channel, version));
                }
            }

            for (var ch : versions.keySet()) {
                result.addChannelDisplayName(ch, this.sources.get(ch).meta().name());
            }

            MCUProtocolV2.sendPacket(ctx, result);
            return;
        }
    }
}
