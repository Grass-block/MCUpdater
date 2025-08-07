package org.atcraftmc.updater.data;

import com.google.gson.JsonObject;

public final class UpdateChannelMeta {
    private final String id;
    private final String name;
    private final String desc;
    private final boolean required;

    public UpdateChannelMeta(String id, String name, String desc, boolean required) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.required = required;
    }

    public UpdateChannelMeta(JsonObject dom) {
        this.id = dom.get("id").getAsString();
        this.name = dom.get("name").getAsString();
        this.desc = dom.get("desc").getAsString();
        this.required = dom.get("required").getAsBoolean();
    }

    public boolean required() {
        return required;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String desc() {
        return desc;
    }

    public JsonObject json() {
        var dom = new JsonObject();
        dom.addProperty("id", this.id);
        dom.addProperty("name", this.name);
        dom.addProperty("desc", this.desc);
        dom.addProperty("required", this.required);

        return dom;
    }
}
