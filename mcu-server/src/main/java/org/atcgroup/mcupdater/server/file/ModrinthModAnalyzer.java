package org.atcgroup.mcupdater.server.file;

import com.google.gson.JsonParser;
import me.gb2022.commons.http.HttpMethod;
import me.gb2022.commons.http.HttpRequest;
import me.gb2022.commons.math.SHA;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.data.HttpDownloadInfo;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

//ok啊小小测试一把
//走你
public final class ModrinthModAnalyzer implements FileAddHandler {

    public static final Logger LOGGER = LogManager.getLogger("FileAnalyzer/Modrinth");
    private final Map<String, HttpDownloadInfo> urlCache = new HashMap<>();
    private final List<String> matchExtensions;
    private final String url;

    public ModrinthModAnalyzer(ConfigurationSection dom) {
        this.matchExtensions = dom.getStringList("detect-ext-name");
        this.url = dom.getString("base-url");
    }

    static String getExtensionName(File file) {
        var name = file.getName();
        var idx = name.lastIndexOf('.');

        if (idx <= 0) {
            return name;
        }

        return name.substring(idx + 1);
    }

    @Override
    public boolean handleNewFile(String relPath, File file, byte[] sha1, VersionInfo info) {
        if (this.matchExtensions.contains(getExtensionName(file))) {
            if (!file.getName().endsWith(".jar")) {
                return false;
            }
        }

        if (this.urlCache.size() > 16384) {
            this.urlCache.clear();
        }

        var key = new String(sha1, StandardCharsets.UTF_8);

        if (this.urlCache.containsKey(key)) {
            info.addExternalDownloadFile(this.urlCache.get(key));
            return true;
        }

        var request = HttpRequest.https(HttpMethod.GET, this.url)
                .path("/version_file/")
                .path(SHA.byteArrayToHexString(sha1))
                .param("algorithm", "sha1")
                .header("User-Agent", "org/atcgroup/mcupdater-server")
                .browserBehavior(false)
                .build();

        var dom = request.request();

        if (dom.isEmpty()) {
            return false;
        }

        var json = JsonParser.parseString(dom).getAsJsonObject();

        if (!json.has("name")) {
            return false;
        }

        var name = json.get("name").getAsString();
        var version = json.get("version_number").getAsString();

        LOGGER.info("detected modrinth MOD: {}@{}", name, version);

        var fileSect = json.getAsJsonArray("files").get(0).getAsJsonObject();
        var url = fileSect.get("url").getAsString();
        var sha1_t = fileSect.getAsJsonObject("hashes").get("sha1").getAsString();
        var size = fileSect.get("size").getAsLong();

        var i = HttpDownloadInfo.of(url, relPath, size, sha1_t);

        info.addExternalDownloadFile(i);
        this.urlCache.put(key, i);

        return true;
    }
}
