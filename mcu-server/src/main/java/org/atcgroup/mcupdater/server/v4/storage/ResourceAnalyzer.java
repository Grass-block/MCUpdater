package org.atcgroup.mcupdater.server.v4.storage;

import com.google.gson.JsonParser;
import me.gb2022.commons.http.HttpMethod;
import me.gb2022.commons.http.HttpRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ResourceAnalyzer {
    Optional<CASObjectMeta> analyze(File file, String sha1, String sha256);

    final class ModrinthResourceAnalyzer implements ResourceAnalyzer {
        public static final Logger LOGGER = LogManager.getLogger("ResourceAnalyzer/Modrinth");

        private final Map<String, String> urlCache = new HashMap<>();
        private final Set<String> matchExtensions;
        private final String url;

        public ModrinthResourceAnalyzer(String url, Set<String> matchExtensions) {
            this.url = url;
            this.matchExtensions = matchExtensions;
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
        public Optional<CASObjectMeta> analyze(File file, String sha1, String sha512) {
            if (this.urlCache.containsKey(sha1)) {
                return Optional.of(new CASObjectMeta(file.length(), sha1, sha512, this.urlCache.get(sha1)));
            }

            if (!this.matchExtensions.contains(getExtensionName(file))) {
                return Optional.empty();
            }

            var request = HttpRequest.https(HttpMethod.GET, this.url)
                                     .path("/version_file/")
                                     .path(sha1)
                                     .param("algorithm", "sha1")
                                     .header("User-Agent", "org/atcgroup/mcupdater-server")
                                     .browserBehavior(false)
                                     .build();

            var dom = request.request();

            if (dom.isEmpty()) {
                return Optional.empty();
            }

            var json = JsonParser.parseString(dom).getAsJsonObject();

            if (!json.has("name")) {
                return Optional.empty();
            }

            var name = json.get("name").getAsString();
            var version = json.get("version_number").getAsString();

            LOGGER.info("detected modrinth Artifact: {}@{}", name, version);

            var fileSect = json.getAsJsonArray("files").get(0).getAsJsonObject();
            var url = fileSect.get("url").getAsString();
            var size = fileSect.get("size").getAsLong();

            this.urlCache.put(sha1, url);
            return Optional.of(new CASObjectMeta(size, sha1, sha512, url));
        }
    }
}
