package me.gb2022.mcupdater.data;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TranslatableComponent {
    private final Map<String, Object> translations;

    public TranslatableComponent(Map<String, Object> translations) {
        this.translations = translations;
    }

    public String get(String language) {
        if (this.translations.containsKey(language)) {
            return this.translations.get(language).toString();
        }

        return this.translations.get(this.translations.keySet().iterator().next()).toString();
    }

    public String get() {
        return get(Locale.getDefault().toString().toLowerCase());
    }

    public void encode(ByteBuf buffer) {
        buffer.writeByte(this.translations.size());
        for (var entry : this.translations.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            BufferUtil.writeString(buffer, entry.getValue().toString());
        }
    }

    public TranslatableComponent(ByteBuf buffer) {
        this.translations = new HashMap<>();
        var b = buffer.readByte();
        for (int i = 0; i < b; i++) {
            var key = BufferUtil.readString(buffer);
            var value = BufferUtil.readString(buffer);
            this.translations.put(key, value);
        }
    }
}
