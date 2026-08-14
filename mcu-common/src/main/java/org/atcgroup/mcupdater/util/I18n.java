package org.atcgroup.mcupdater.util;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class I18n {
    public static final I18n INSTANCE = new I18n();
    private final Map<String, String> messages = new HashMap<>();

    public static I18n instance() {
        return INSTANCE;
    }

    public static String message(String key, Object... args) {
        return INSTANCE.getMessage(key, args);
    }

    public void load(InputStream resource) {
        try (var in = new InputStreamReader(resource)) {
            var root = JsonParser.parseReader(in).getAsJsonObject();
            var locale = Locale.getDefault().toLanguageTag();

            for (var key : root.keySet()) {
                var entry = root.get(key).getAsJsonObject().get(locale);

                if (entry == null) {
                    continue;
                }

                this.messages.put(key, entry.getAsString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getMessage(String key, Object... args) {
        var message = this.messages.get(key);
        if (message == null) {
            return "_NO_TRANSLATION(%s)".formatted(key);
        }

        return new MessageFormat(message).format(args);
    }
}
