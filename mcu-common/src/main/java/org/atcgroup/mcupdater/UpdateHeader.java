package org.atcgroup.mcupdater;

import org.atcgroup.mcupdater.data.HttpDownloadInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class UpdateHeader {
    private final Set<CompiledVersionData> versions = new HashSet<>();
    private final Map<String, String> serverDownloads = new HashMap<>();     //res-id:sha256
    private final Set<HttpDownloadInfo> externDownloads = new HashSet<>();  //info
    private final Map<String, String> replaceFiles = new HashMap<>();        //res-id:dest
    private final Set<String> expandFiles = new HashSet<>();
    private final Set<String> deleteFiles = new HashSet<>();

    public void addVersionData(CompiledVersionData data) {
        this.versions.add(data);
    }

    public Set<CompiledVersionData> getVersions() {
        return versions;
    }

    public void addServerDownload(String resourceId, String sha256) {
        this.serverDownloads.put(resourceId, sha256);
    }

    public void addExternDownload(HttpDownloadInfo downloadInfo) {
        this.externDownloads.add(downloadInfo);
    }

    public void addReplaceFile(String resourceId, String dest) {
        this.replaceFiles.put(resourceId, dest);
    }

    public void addDeleteFile(String fileName) {
        this.deleteFiles.add(fileName);
    }

    public void addExpandFile(String resourceId) {
        this.expandFiles.add(resourceId);
    }

    public Map<String, String> getServerDownloads() {
        return serverDownloads;
    }

    public Set<HttpDownloadInfo> getExternDownloads() {
        return externDownloads;
    }

    public Map<String, String> getReplaceFiles() {
        return replaceFiles;
    }

    public Set<String> getExpandFiles() {
        return expandFiles;
    }

    public Set<String> getDeleteFiles() {
        return deleteFiles;
    }
}
