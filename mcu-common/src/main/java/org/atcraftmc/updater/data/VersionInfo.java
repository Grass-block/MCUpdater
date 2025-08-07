package org.atcraftmc.updater.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class VersionInfo {
    private final String channel;
    private final String version;
    private final long timestamp;
    private final Set<String> update;
    private final Set<String> remove;
    private final Set<String> resourcePack;

    public VersionInfo(String channel, String version, long timestamp, Set<String> update, Set<String> remove, Set<String> resourcePack) {
        this.channel = channel;
        this.version = version;
        this.timestamp = timestamp;
        this.update = new HashSet<>(update);
        this.remove = new HashSet<>(remove);
        this.resourcePack = new HashSet<>(resourcePack);

        this.remove.removeIf(String::isEmpty);
        this.update.removeIf(String::isEmpty);
        this.resourcePack.removeIf(String::isEmpty);
    }

    public VersionInfo(JsonObject dom) {
        this.channel = dom.get("channel").getAsString();
        this.version = dom.get("version").getAsString();
        this.timestamp = dom.get("timestamp").getAsLong();
        this.update = dom.getAsJsonArray("update").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
        this.remove = dom.getAsJsonArray("remove").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
        this.resourcePack = dom.getAsJsonArray("resource_pack").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
    }

    public static VersionInfo ofMerged(List<VersionInfo> list) {
        list.sort(Comparator.comparingLong(VersionInfo::timestamp));

        var update = new HashSet<String>();
        var remove = new HashSet<String>();
        var resourcePack = new HashSet<String>();
        var latest = list.get(list.size() - 1);

        for (var v : list) {
            update.addAll(v.update);
            remove.addAll(v.remove);
            resourcePack.addAll(v.resourcePack);
        }

        remove.removeIf(String::isEmpty);
        update.removeIf(String::isEmpty);
        resourcePack.removeIf(String::isEmpty);

        return new VersionInfo(latest.channel, latest.version, latest.timestamp(), update, remove, resourcePack);
    }

    public JsonObject json() {
        var dom = new JsonObject();
        dom.addProperty("channel", this.channel);
        dom.addProperty("version", this.version);
        dom.addProperty("timestamp", this.timestamp);
        var update = new JsonArray();

        for (var s : this.update) {
            update.add(s);
        }

        dom.add("update", update);
        var remove = new JsonArray();
        for (var s : this.remove) {
            remove.add(s);
        }

        dom.add("remove", remove);

        var res = new JsonArray();
        for (var s : this.resourcePack) {
            res.add(s);
        }
        dom.add("resource_pack", res);
        return dom;
    }

    public String channel() {
        return channel;
    }

    public String version() {
        return version;
    }

    public Set<String> resourcePack() {
        return resourcePack;
    }

    public long timestamp() {
        return timestamp;
    }

    public Set<String> update() {
        return update;
    }

    public Set<String> remove() {
        return remove;
    }
}
