package org.atcgroup.mcupdater.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ClientInstallationInfo {
    private final Map<String, Long> localVersions = new HashMap<>();

    private File dataFile() {
        var file = ClientFilePath.VERSION_INFO.file();

        if (!file.exists() || file.length() == 0) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return file;
    }

    public void load() {
        this.localVersions.clear();

        var properties = new Properties();

        try (var in = new FileInputStream(dataFile())) {
            properties.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (var id : properties.keySet()) {
            this.localVersions.put(id.toString(), Long.valueOf(properties.getProperty(id.toString())));
        }
    }

    public void save() {
        var properties = new Properties();

        for (var id : this.localVersions.keySet()) {
            properties.setProperty(id, String.valueOf(this.localVersions.get(id)));
        }

        try (var out = new FileOutputStream(dataFile())) {
            properties.store(out, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Long> getLocalVersions() {
        return localVersions;
    }

    public boolean isInvalid() {
        return this.localVersions.isEmpty();
    }

    public boolean isEnabled(String id) {
        if (!this.localVersions.containsKey(id)) {
            return false;
        }

        return this.localVersions.get(id) > 0;
    }

    public void setEnabled(String id, boolean enabled) {
        if (!this.localVersions.containsKey(id)) {
            this.localVersions.put(id, 1L);
            //no return because we correct them below
        }

        var value = this.localVersions.getOrDefault(id, 0L);
        if ((value >= 0 && enabled) || (value < 0 && !enabled)) {
            return;
        }

        this.localVersions.put(id, -value);
    }

    public void setTime(String id, long timestamp) {
        this.localVersions.put(id, timestamp);
    }

    public long getTime(String id) {
        return this.localVersions.getOrDefault(id, 0L);
    }
}
