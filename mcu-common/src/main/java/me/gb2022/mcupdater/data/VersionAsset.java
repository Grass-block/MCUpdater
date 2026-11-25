package me.gb2022.mcupdater.data;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

public class VersionAsset {
    public static final String PROVIDER_RTP = "rtp";
    public static final String PROVIDER_HTTP = "http";

    private final String resourceKey;
    private final String provider;

    public VersionAsset(String resourceKey, String provider) {
        this.resourceKey = resourceKey;
        this.provider = provider;
    }

    public final String getProvider() {
        return provider;
    }

    public final String getResourceKey() {
        return resourceKey;
    }

    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.resourceKey);
        BufferUtil.writeString(buffer, this.provider);
    }

    public void write(JsonObject json) {
        json.addProperty("provider", this.provider);
    }

    public static VersionAsset read(String key, JsonObject object) {
        var provider = object.get("provider").getAsString();

        if (provider.equals(PROVIDER_RTP)) {
            return new RTPAsset(key);
        }
        if (provider.equals(PROVIDER_HTTP)) {
            return new HttpAsset(key, object);
        }

        throw new IllegalArgumentException("Unknown provider: " + provider);
    }

    public static VersionAsset read(ByteBuf buffer) {
        var resourceKey = BufferUtil.readString(buffer);
        var provider = BufferUtil.readString(buffer);

        if (provider.equals(PROVIDER_RTP)) {
            return new RTPAsset(resourceKey);
        }
        if (provider.equals(PROVIDER_HTTP)) {
            return new HttpAsset(resourceKey, buffer);
        }

        throw new IllegalArgumentException("Unknown provider: " + provider);
    }

    public static final class RTPAsset extends VersionAsset {
        public RTPAsset(String resourceKey) {
            super(resourceKey, PROVIDER_RTP);
        }
    }

    public static final class HttpAsset extends VersionAsset {
        private final String url;
        private final long length;

        public HttpAsset(String resourceKey, ByteBuf buffer) {
            super(resourceKey, PROVIDER_HTTP);
            this.url = BufferUtil.readString(buffer);
            this.length = validateLength(buffer.readLong());
        }

        public HttpAsset(String resourceKey, JsonObject object) {
            super(resourceKey, PROVIDER_HTTP);
            this.url = object.get("url").getAsString();
            this.length = validateLength(object.get("length").getAsLong());
        }

        private static long validateLength(long length) {
            if (length < 0) throw new IllegalArgumentException("length must not be negative");
            return length;
        }

        public long getLength() {
            return length;
        }

        public String getUrl() {
            return url;
        }

        @Override
        public void write(ByteBuf buffer) {
            super.write(buffer);
            BufferUtil.writeString(buffer, this.url);
            buffer.writeLong(this.length);
        }

        @Override
        public void write(JsonObject json) {
            super.write(json);
            json.addProperty("url", this.url);
            json.addProperty("length", this.length);
        }
    }
}
