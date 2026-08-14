package org.atcgroup.mcupdater.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

public final class VersionInfo {
    private final String channel;
    private final long timestamp;
    private final String version;
    private final Set<String> deleteFileList;
    private final Set<String> downloadPackList;
    private final Map<String, String> updateFileList;
    private final Map<String, String> downloadFileList;

    public VersionInfo(String channel, long timestamp, String version, Set<String> deleteFileList, Set<String> downloadPackList, Map<String, String> updateFileList, Map<String, String> downloadFileList) {
        this.channel = channel;
        this.timestamp = timestamp;
        this.version = version;
        this.downloadPackList = downloadPackList;
        this.deleteFileList = deleteFileList;
        this.updateFileList = updateFileList;
        this.downloadFileList = downloadFileList;
    }

    public VersionInfo(JsonObject dom) {
        this.channel = dom.get("channel").getAsString();
        this.version = dom.get("version").getAsString();
        this.timestamp = dom.get("timestamp").getAsLong();
        this.deleteFileList = dom.getAsJsonArray("remove").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
        this.downloadPackList = dom.getAsJsonArray("resource_pack")
                .asList()
                .stream()
                .map(JsonElement::getAsString)
                .collect(Collectors.toSet());
        this.updateFileList = new HashMap<>();//reserve
        this.downloadFileList = new HashMap<>();//reserve
    }

    public static VersionInfo ofMerged(List<VersionInfo> list) {
        list.sort(Comparator.comparingLong(VersionInfo::getTimestamp));

        var packs = new HashSet<String>();
        var remove = new HashSet<String>();
        var update = new HashMap<String, String>();
        var download = new HashMap<String, String>();
        var latest = list.get(list.size() - 1);

        for (var v : list) {
            remove.addAll(v.getDeleteFileList());
            packs.addAll(v.getDownloadPackList());
            update.putAll(v.getUpdateFileList());
            download.putAll(v.getDownloadFileList());
        }

        remove.removeIf(String::isEmpty);
        packs.removeIf(String::isEmpty);

        return new VersionInfo(latest.channel, latest.timestamp, latest.version, remove, packs, update, download);
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
        for (var s : this.downloadPackList) {
            res.add(s);
        }
        dom.add("resource_pack", res);
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

    public Set<String> getDownloadPackList() {
        return downloadPackList;
    }

    public Map<String, String> getUpdateFileList() {
        return updateFileList;
    }

    public Map<String, String> getDownloadFileList() {
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
        sb.append(", downloadPackList=").append(downloadPackList);
        sb.append(", updateFileList=").append(updateFileList);
        sb.append(", downloadFileList=").append(downloadFileList);
        sb.append('}');
        return sb.toString();
    }
}
