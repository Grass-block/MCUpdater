package org.atcraftmc.updater.client.util;

import java.util.HashSet;
import java.util.Set;

public interface DeferredTaskManager {
    Set<Runnable> ADD_FILE = new HashSet<>();
    Set<Runnable> DELETE_FILE = new HashSet<>();
    Set<Runnable> COMMON = new HashSet<>();

    static void commonTask(Runnable action) {
        COMMON.add(action);
    }

    static void addFileTask(Runnable action) {
        ADD_FILE.add(action);
    }

    static void deleteFileTask(Runnable action) {
        DELETE_FILE.add(action);
    }

    static void batch() {
        ADD_FILE.forEach(Runnable::run);
        DELETE_FILE.forEach(Runnable::run);
        COMMON.forEach(Runnable::run);
    }
}
