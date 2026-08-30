package org.atcgroup.mcupdater.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

public final class HttpDownloadInfo {
    private final String url;
    private final String dest;
    private final String sha1;
    private final long size;


    public HttpDownloadInfo(String url, String dest, String sha1, long size) {
        this.url = url;
        this.dest = dest;
        this.sha1 = sha1;
        this.size = size;
    }

    public static HttpDownloadInfo of(String url, String dest, long size, String sha512) {
        return new HttpDownloadInfo(url, dest, sha512, size);
    }

    public String getUrl() {
        return url;
    }

    public long getSize() {
        return size;
    }

    public String getSha1() {
        return sha1;
    }

    public String getDest() {
        return dest;
    }

    public Runnable createDownloadTask() {
        //todo
        return null;
    }

    public static Set<HttpDownloadInfo> fromJson(JsonArray ja) {
        var result = new HashSet<HttpDownloadInfo>();
        for (var e : ja) {
            var d = e.getAsJsonObject();

            var url = d.get("url").getAsString();
            var dest = d.get("dest").getAsString();
            var size = d.get("size").getAsLong();
            var sha512 = d.get("sha1").getAsString();

            result.add(HttpDownloadInfo.of(url, dest, size, sha512));
        }

        return result;
    }

    public JsonElement json() {
        var obj = new JsonObject();
        obj.addProperty("url", this.url);
        obj.addProperty("dest", this.dest);
        obj.addProperty("sha1", this.sha1);
        obj.addProperty("size", this.size);
        return obj;
    }
}
