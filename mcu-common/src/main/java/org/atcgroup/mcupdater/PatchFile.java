package org.atcgroup.mcupdater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

//map<file,hash>
public interface PatchFile {
    static Map<String, String> decodeFileMap(ZipFile file) throws IOException {
        var e = file.getEntry("pack-meta.json");
        var in = file.getInputStream(e);

        try (var reader = new InputStreamReader(in)) {
            var dom = JsonParser.parseReader(reader).getAsJsonObject();
            var map = new HashMap<String, String>();

            for (var hash : dom.keySet()) {
                map.put(dom.get(hash).getAsString(), hash);
            }

            return map;
        }
    }

    static Map<String, String> generateFileMap(Map<String, File> collected) {
        var map = new HashMap<String, String>();

        for (var file : collected.keySet()) {
            map.put(file, UUID.randomUUID().toString());
        }

        return map;
    }

    static void encodeFileMap(ZipOutputStream stream, Map<String, String> mapping) throws IOException {
        var dom = new JsonObject();

        for (var file : mapping.keySet()) {
            dom.addProperty(mapping.get(file), file);
        }

        stream.putNextEntry(new ZipEntry("pack-meta.json"));
        stream.write(dom.toString().getBytes());
        stream.closeEntry();
    }



    static void unzip(File handle, String base) {
        unzip(handle, base, (a, b) -> {});
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void unzip(File handle, String base, BiConsumer<Integer, Integer> progressHandler) {
        try (var zip = new ZipFile(handle)) {
            var map = decodeFileMap(zip);

            var all = map.size();
            var count = 1;

            for (var entry : map.entrySet()) {
                progressHandler.accept(count, all);

                var path = entry.getKey();
                var hash = entry.getValue();

                var file = new File(base + path);

                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }

                try (var o = new FileOutputStream(file, false)) {
                    o.write(zip.getInputStream(zip.getEntry(hash)).readAllBytes());
                }

                count++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void zip(File handle, Map<String, File> collected) {
        if (!handle.exists()) {
            handle.getParentFile().mkdirs();
            try {
                handle.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try (var zip = new ZipOutputStream(new FileOutputStream(handle))) {
            var map = generateFileMap(collected);

            for (var name : collected.keySet()) {
                var hash = map.get(name);

                try (var stream = new FileInputStream(collected.get(name))) {
                    zip.putNextEntry(new ZipEntry(hash));
                    zip.write(stream.readAllBytes());
                    zip.closeEntry();
                }
            }

            encodeFileMap(zip, map);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
