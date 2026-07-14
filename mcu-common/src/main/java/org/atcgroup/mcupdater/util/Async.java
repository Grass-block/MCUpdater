package org.atcgroup.mcupdater.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface Async {
    ExecutorService WORKER = Executors.newCachedThreadPool();
}
