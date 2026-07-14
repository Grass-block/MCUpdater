package org.atcraftmc.updater.server;

import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcraftmc.updater.server.service.FileService;
import org.atcraftmc.updater.server.service.ConsoleService;
import org.atcraftmc.updater.util.FilePath;
import org.atcgroup.mcupdater.PatchFile;
import org.atcraftmc.updater.data.VersionInfo;
import org.atcgroup.mcupdater.server.file.FileModifyStatus;
import org.atcraftmc.updater.network.packet.*;
import org.atcgroup.mcupdater.util.DiffCheck;
import org.atcraftmc.updater.server.service.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class MCUpdaterServer {
    public static final Logger LOGGER = LogManager.getLogger("Server");
    private final FileConfiguration config = new YamlConfiguration();
    private final FileService fileService = new FileService(this);
    private final ConsoleService console = new ConsoleService(this);
    private final NetworkService network = new NetworkService(this);
    private final VersionService version = new VersionService(this, this.fileService);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    public CDNService cdn;

    public boolean loadConfiguration() {
        var file = new File(FilePath.runtime() + "/config.yml");

        if (!file.exists() || file.length() == 0) {
            LOGGER.warn("没有找到默认的配置文件，正在覆盖生成...");

            try (var out = new FileOutputStream(file); var in = this.getClass().getResourceAsStream("/config.yml")) {
                out.write(Objects.requireNonNull(in).readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            LOGGER.info("配置文件生成于 {}", file.getAbsolutePath());

            return false;
        }

        try {
            this.config.load(file);
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.warn("读取配置文件时发生错误!");
            LOGGER.catching(e);

            return false;
        }
    }

    public void init() {
        this.console.start();
        this.console.waitFor();

        LOGGER.info("正在加载配置");
        if (!loadConfiguration()) {
            LOGGER.warn("读取配置文件时遇到了一些问题，请重新配置并启动服务器。");
            System.exit(1);
        }

        this.fileService.start();
        this.version.start();
        this.network.start();
        this.cdn = new CDNService(this);
        this.cdn.start();
    }

    public void stop() {
        this.console.stop();
        this.network.stop();
        this.cdn.stop();
    }


    //actions
    public void buildVersion(String[] command) {
        if (command.length < 3) {
            LOGGER.error("正确用法: build <频道> <版本>");
            return;
        }

        if (this.cdn.isUploading()) {
            LOGGER.error("请等待CDN上传任务全部结束后再制作版本! ");
        }

        var channel = command[1];
        var version = command[2];

        if (!this.fileService.sources().containsKey(channel)) {
            LOGGER.error("更新频道 {} 不存在。", channel);
            return;
        }

        if (this.version.channel(channel).containsKey(version)) {
            LOGGER.error("版本号 {} 已经存在。", version);
            return;
        }

        LOGGER.info("正在检查文件状态...");
        var source = this.fileService.sources().get(channel);
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

        var uuid = UUID.randomUUID().toString();
        var zipFile = new File(FilePath.resourcePack(channel, uuid));
        var files = new HashMap<String, File>();

        for (var path : update) {
            var file = this.fileService.sources().get(channel).file(path);
            files.put(path, file);
        }

        LOGGER.info("正在压缩 {} 个文件到资源包...", files.size());

        PatchFile.zip(zipFile, files);

        LOGGER.info("压缩包已创建: {}", zipFile.getAbsolutePath());

        var time = System.currentTimeMillis();
        var vi = new VersionInfo(channel, version, time, new HashSet<>(), remove, Set.of(FilePath.resourcePackId(channel, uuid)));

        this.version.registerVersion(vi);

        LOGGER.info("版本已创建: {}-{} 打包时间: {}", channel, version, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time));
    }

    public void uploadPacks() {
        for (var v : versions().all()) {
            for (var p : v.resourcePack()) {
                var f = new File(FilePath.runtime() + "/packs/" + p + ".zip");

                String sha256;
                try {
                    sha256 = new String(DiffCheck.calculateSHA256(f.getAbsolutePath()), StandardCharsets.UTF_8);
                } catch (IOException | NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                var remoteSha256 = this.cdn.getCDNFileStatus(p + ".zip");

                if (sha256.equals(remoteSha256)) {
                    continue;
                }

                this.cdn.planUpload(p);
            }
        }
        this.cdn.batchUpload();
    }

    public String versionLog(String id) {
        var f = new File(FilePath.versions() + "/" + id + ".txt");

        if (!f.exists() || f.length() == 0) {
            return "没有版本信息 :(";
        }


        try (var in = new FileInputStream(f)) {
            return new String(in.readAllBytes());
        } catch (Exception e) {
            return "没有版本信息 :(";
        }
    }


    //update
    private void sendAddFileResponse(ArrayList<pair> jobs, ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(new P0F_ServerProgressUpdate("正在准备文件..."));

        var totalSize = 0L;

        for (var f : jobs) {
            var channel = f.channel();
            var file = f.file();

            totalSize += this.fileService.sources().get(channel).file(file).length();
        }

        ctx.writeAndFlush(new P1F_UpdateProgressPredict(totalSize));

        var size = 0;
        var packet = new P11_FileExpand();

        for (var data : jobs) {
            var file = this.fileService.sources().get(data.channel()).file(data.file());

            try (var in = new FileInputStream(file)) {
                var payload = in.readAllBytes();
                size += payload.length;
                packet.add(data.file(), payload);

                if (size > 262144) {
                    Thread.sleep(config().getInt("rate-delay", 0));
                    ctx.writeAndFlush(packet);
                    packet = new P11_FileExpand();
                    size = 0;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        ctx.writeAndFlush(packet);
    }

    private String sendResourceFileResponse(ArrayList<String> resourcePacks, ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(new P0F_ServerProgressUpdate("准备下载资源包..."));

        var cdnPacks = new HashSet<String>();
        var enableCDN = config().getBoolean("cdn-server.enable", false);

        for (var pack : resourcePacks) {
            var file = new File(FilePath.runtime() + "/packs/" + pack + ".zip");
            var id = pack + ".zip";

            var cdn_sum = this.cdn.getCDNFileStatus(id);
            var sum = new String(DiffCheck.calculateSHA256(file.getAbsolutePath()), StandardCharsets.UTF_8);

            if (sum.equals(cdn_sum)) {
                cdnPacks.add(pack);
                continue;
            } else {
                if (enableCDN) {
                    LOGGER.warn("有未上传的数据包，请使用 'cdn-upload' 指令手动同步！");
                }
            }

            LOGGER.info("start: {} - {}bytes", pack, file.length());
            sendPatchFile(file, ctx);
        }

        var ip = config().getString("cdn-server.address");
        var port = config().getInt("cdn-server.port");

        var id = UUID.randomUUID().toString();
        ctx.writeAndFlush(new P15_CDNDownloads(id, enableCDN ? ip : "_", port, this.cdn.getRepository(), cdnPacks));
        return id;
    }

    private void sendPatchFile(File file, ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(new P13_PatchFileInfo((int) file.length(), file.lastModified(), ""));
        var slice = config().getInt("patch-slice-size", 8192);
        var buffer = new byte[slice];
        var fin = new FileInputStream(file);
        var bin = new BufferedInputStream(fin);
        var length = 0;

        while ((length = bin.read(buffer)) != -1) {
            var data = new byte[length];
            System.arraycopy(buffer, 0, data, 0, length);
            ctx.writeAndFlush(new P14_PatchFileSlice(data, 0)).get();
        }

        ctx.writeAndFlush(new P14_PatchFileSlice(new byte[0], org.atcraftmc.updater.network.packet.P14_PatchFileSlice.SIG_END));
    }

    public void sendUpdateDataResponse(org.atcraftmc.updater.network.packet.P10_VersionInfo vi, ChannelHandlerContext ctx) {
        LOGGER.info("got update request from {}", ctx.channel().remoteAddress());

        try {
            ctx.writeAndFlush(new P0F_ServerProgressUpdate("正在合并版本信息..."));

            var addFiles = new ArrayList<pair>();
            var resourcePacks = new ArrayList<String>();
            var result = new HashSet<org.atcraftmc.updater.network.packet.P10_VersionInfo.info>();

            ctx.writeAndFlush(new P0F_ServerProgressUpdate("正在分析版本并移除过时的文件..."));

            for (var v : vi.getInfos()) {
                var version = versions().from(v.id(), v.timestamp());

                if (version == null) {
                    continue;
                }

                ctx.writeAndFlush(new P12_FileDelete(version.remove().toArray(new String[0])));
                resourcePacks.addAll(version.resourcePack());
                addFiles.addAll(version.update().stream().map((s) -> new pair(version.channel(), s)).collect(Collectors.toSet()));

                var v_id = v.id();
                var v_version = v.version();
                var v_timestamp = version.timestamp();
                var v_name = this.fileService.sources().get(v_id).meta().name();
                var v_desc = versions().log(v.id(), v.name());

                result.add(new P10_VersionInfo.info(v_id, v_version, v_timestamp, v_name, v_desc));
            }

            if (addFiles.isEmpty()) {
                ctx.writeAndFlush(new P0F_ServerProgressUpdate("未发现文件更新:("));
            } else {
                this.sendAddFileResponse(addFiles, ctx);
            }

            if (resourcePacks.isEmpty()) {
                ctx.writeAndFlush(new P0F_ServerProgressUpdate("未发现资源包更新:("));
                ctx.writeAndFlush(new P10_VersionInfo(result));
            } else {
                var id = this.sendResourceFileResponse(resourcePacks, ctx);
                this.network.addCDNWaitingList(id, new P10_VersionInfo(result));
            }
        } catch (Exception e) {
            LOGGER.catching(e);
        }
    }


    //access
    public ExecutorService getExecutor() {
        return this.executor;
    }

    public ConfigurationSection config() {
        return this.config.getConfigurationSection("config");
    }

    public VersionService versions() {
        return this.version;
    }

    public FileService fileService() {
        return this.fileService;
    }

    record pair(String channel, String file) {
    }
}
