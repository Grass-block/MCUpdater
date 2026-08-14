package org.atcraftmc.updater.client;

public interface ClientEventSource {
    default void callEvent(Event type, Object... event) {
        MCUpdaterClient.INSTANCE.callEvent(type, event);
    }

    default void runTask(Runnable action) {
        MCUpdaterClient.INSTANCE.executor.submit(action);
    }

    default MCUpdaterClient client() {
        return MCUpdaterClient.INSTANCE;
    }
}
