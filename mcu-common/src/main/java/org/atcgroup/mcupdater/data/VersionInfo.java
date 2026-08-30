package org.atcgroup.mcupdater.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;

public final class VersionInfo {
    private final String channel;
    private final long timestamp;
    private final String version;
    private final Set<String> deleteFileList;
    private final Set<String> resourcePackList;
    private final Map<String, String> updateFileList;
    private final Set<HttpDownloadInfo> downloadFileList;

    public VersionInfo(String channel, long timestamp, String version, Set<String> deleteFileList, Set<String> resourcePackList, Map<String, String> updateFileList, Set<HttpDownloadInfo> downloadFileList1) {
        this.channel = channel;
        this.timestamp = timestamp;
        this.version = version;
        this.resourcePackList = resourcePackList;
        this.deleteFileList = deleteFileList;
        this.updateFileList = updateFileList;
        this.downloadFileList = downloadFileList1;
    }

    public VersionInfo(String channel, long timestamp, String version, Set<String> deleteFileList, Map<String, String> updateFileList) {
        this.channel = channel;
        this.timestamp = timestamp;
        this.version = version;
        this.resourcePackList = new HashSet<>();
        this.deleteFileList = deleteFileList;
        this.updateFileList = updateFileList;
        this.downloadFileList = new HashSet<>();
    }

    public VersionInfo(String channel, String version, long timestamp) {
        this.channel = channel;
        this.timestamp = timestamp;
        this.version = version;
        this.resourcePackList = new HashSet<>();
        this.deleteFileList = new HashSet<>();
        this.updateFileList = new HashMap<>();
        this.downloadFileList = new HashSet<>();
    }

    public static VersionInfo fromJson(JsonObject dom) {
        var channel = dom.get("channel").getAsString();
        var version = dom.get("version").getAsString();
        var timestamp = dom.get("timestamp").getAsLong();

        var vi = new VersionInfo(channel, version, timestamp);

        if (dom.has("remove")) {
            for (var e : dom.getAsJsonArray("remove")) {
                vi.addDeleteFile(e.getAsString());
            }
        }

        if (dom.has("resource_pack")) {
            for (var e : dom.getAsJsonArray("resource_pack")) {
                vi.addDownloadPackFile(e.getAsString());
            }
        }

        if (dom.has("update")) {
            for (var e : dom.getAsJsonObject("update").asMap().entrySet()) {
                vi.addUpdateFile(e.getKey(), e.getValue().getAsString());
            }
        }

        if (dom.has("download")) {
            var downloads = HttpDownloadInfo.fromJson(dom.get("download").getAsJsonArray());

            for (var e : downloads) {
                vi.addExternalDownloadFile(e);
            }
        }

        return vi;
    }

    public static VersionInfo ofMerged(List<VersionInfo> list) {
        list.sort(Comparator.comparingLong(VersionInfo::getTimestamp));

        var packs = new HashSet<String>();
        var remove = new HashSet<String>();
        var update = new HashMap<String, String>();
        var download = new HashSet<HttpDownloadInfo>();
        var latest = list.get(list.size() - 1);

        for (var v : list) {
            remove.addAll(v.getDeleteFileList());
            packs.addAll(v.getResourcePackList());
            update.putAll(v.getUpdateFileList());
            download.addAll(v.getDownloadFileList());
        }

        remove.removeIf(String::isEmpty);
        packs.removeIf(String::isEmpty);

        return new VersionInfo(latest.channel, latest.timestamp, latest.version, remove, packs, update, download);
    }

    public void addDeleteFile(String file) {
        this.deleteFileList.add(file);
    }

    public void addDownloadPackFile(String id) {
        this.resourcePackList.add(id);
    }

    public void addUpdateFile(String dest, String id) {
        this.updateFileList.put(dest, id);
    }

    public void addExternalDownloadFile(HttpDownloadInfo request) {
        this.downloadFileList.add(request);
    }

    public JsonObject json() {
        var dom = new JsonObject();
        dom.addProperty("version", this.version);
        dom.addProperty("timestamp", this.timestamp);
        dom.addProperty("channel", this.channel);

        var remove = new JsonArray();
        for (var s : this.deleteFileList) {
            remove.add(s);
        }

        dom.add("remove", remove);

        var res = new JsonArray();
        for (var s : this.resourcePackList) {
            res.add(s);
        }
        dom.add("resource_pack", res);

        var download = new JsonArray();
        for (var d : this.downloadFileList) {
            download.add(d.json());
        }
        dom.add("download", download);

        return dom;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getVersion() {
        return version;
    }

    public Set<String> getDeleteFileList() {
        return deleteFileList;
    }

    public Set<String> getResourcePackList() {
        return resourcePackList;
    }

    public Map<String, String> getUpdateFileList() {
        return updateFileList;
    }

    public Set<HttpDownloadInfo> getDownloadFileList() {
        return downloadFileList;
    }

    public String getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("VersionInfo{");
        sb.append("timeStamp=").append(timestamp);
        sb.append(", version='").append(version).append('\'');
        sb.append(", deleteFileList=").append(deleteFileList);
        sb.append(", downloadPackList=").append(resourcePackList);
        sb.append(", updateFileList=").append(updateFileList);
        sb.append(", downloadFileList=").append(downloadFileList);
        sb.append('}');
        return sb.toString();
    }
}
