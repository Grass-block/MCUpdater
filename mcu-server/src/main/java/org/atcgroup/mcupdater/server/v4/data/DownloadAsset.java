package org.atcgroup.mcupdater.server.v4.data;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.Objects;

public final class DownloadAsset {
    private final String url;
    private final long length;

    public DownloadAsset(String url, long length) {
        this.url = Objects.requireNonNull(url, "url");
        if (length < 0) throw new IllegalArgumentException("length must not be negative");
        this.length = length;
    }

    public DownloadAsset(ByteBuf buffer) {
        this(BufferUtil.readString(buffer), buffer.readLong());
    }

    public void encode(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.url);
        buffer.writeLong(this.length);
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("url", this.url);
        json.addProperty("length", this.length);
        return json;
    }

    public String getUrl() { return url; }
    public long getLength() { return length; }
}
