package org.atcgroup.mcupdater.server.file;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import me.gb2022.commons.http.HttpMethod;
import org.atcgroup.mcupdater.data.VersionInfo;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

//todo: CF API需要个人APIKey，暂时不再做支持。
//SHIT!
public final class CurseForgeModAnalyzer implements FileAddHandler {
    private static final int CF_FP_MAGIC = 0x5BD1E995; // 1540483477 WTF?

    private final List<String> matchExtensions;
    private final String url;
    private final String apiKey;

    public CurseForgeModAnalyzer(ConfigurationSection dom) {
        this.matchExtensions = dom.getStringList("detect-ext-name");
        this.url = dom.getString("base-url");
        this.apiKey = dom.getString("user-token");
    }

    public static long calculate(Path file) throws IOException {
        int normalizedLength = 0;

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];

            int read;
            while ((read = in.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (!isWhitespace(buffer[i] & 0xFF)) {
                        normalizedLength++;
                    }
                }
            }
        }

        int hash = 1 ^ normalizedLength;

        int packed = 0;
        int packedBits = 0;

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];

            int read;
            while ((read = in.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    int b = buffer[i] & 0xFF;

                    if (isWhitespace(b)) {
                        continue;
                    }

                    packed |= b << packedBits;
                    packedBits += 8;

                    if (packedBits == 32) {
                        int num6 = packed * CF_FP_MAGIC;
                        int num7 = (num6 ^ (num6 >>> 24)) * CF_FP_MAGIC;

                        hash = hash * CF_FP_MAGIC ^ num7;

                        packed = 0;
                        packedBits = 0;
                    }
                }
            }
        }

        if (packedBits > 0) {
            hash = (hash ^ packed) * CF_FP_MAGIC;
        }

        int num6 = (hash ^ (hash >>> 13)) * CF_FP_MAGIC;

        return Integer.toUnsignedLong(num6 ^ (num6 >>> 15));
    }

    private static boolean isWhitespace(int b) {
        return b == 9      // \t
                || b == 10 // \n
                || b == 13 // \r
                || b == 32; // space
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        String json = """
                {
                    "fingerprints": [123456789]
                }
                """;

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://api.curseforge.com/v1/fingerprints/432"
                ))
                .header("X-API-KEY", "4579efa8-d2f6-4c56-afca-9e41e907f37f")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        System.out.println("Method: " + request.method());
        System.out.println("URI: " + request.uri());
        System.out.println("Headers: " + request.headers().map());

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Headers: " + response.headers().map());
        System.out.println("Body:");
        System.out.println(response.body());
    }

    @Override
    public void handleFiles(Set<SourceFileInfo> files, VersionInfo info) {
        var map = new HashMap<Long, SourceFileInfo>();
        var json = new JsonArray();

        for (var i : files) {
            long fingerprint;
            try {
                fingerprint = calculate(i.file().toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            map.put(fingerprint, i);
            json.add(fingerprint);
        }

        System.out.println(apiKey);

        var dom = "{\"fingerprints\": " + json + "}";
        var req = me.gb2022.commons.http.HttpRequest.https(HttpMethod.POST, this.url)
                .path("/fingerprints/432")
                .header("X-API-KEY", this.apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        System.out.println(req.getUrl());
        System.out.println(req.getHeaders());

        var resultJson = req.requestWithPayload(dom);

        System.out.println(resultJson);

        var result = JsonParser.parseString(resultJson);


    }

    @Override
    public boolean handleNewFile(String relPath, File file, byte[] sha1, VersionInfo info) {
        return false;
    }
}
