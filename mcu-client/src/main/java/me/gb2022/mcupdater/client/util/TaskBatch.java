package me.gb2022.mcupdater.client.util;

import me.gb2022.mcupdater.client.ui.ClientUI;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class TaskBatch {
    private final List<Task> mainTasks = new ArrayList<>();
    private final Set<Task> asyncTasks = new HashSet<>();

    public void createMainTask(Consumer<Task> handler){
        this.mainTasks.add(new Task(handler,TaskCallback.mainTask()));
    }

    public void submitAsyncTask(Consumer<Task> handler){
        this.asyncTasks.add(new Task(handler,TaskCallback.asyncTask(UUID.randomUUID().toString())));
    }

    public void emit(ClientUI ui){
        var waits = new HashSet<Future<?>>();
        var executor = Executors.newFixedThreadPool(Math.max(1, Math.min(4, this.asyncTasks.size())));

        this.mainTasks.forEach(task -> task.callback.__init(ui));
        this.asyncTasks.forEach(task -> task.callback.__init(ui));

        try {
            for (var task : this.asyncTasks) {
                waits.add(executor.submit(task));
            }
            for (var task : this.mainTasks) {
                task.run();
            }
            for (var future : waits) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task batch interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Task batch failed", e.getCause());
        } finally {
            waits.forEach(future -> future.cancel(true));
            executor.shutdownNow();
        }
    }


    public static final class Task implements Runnable {
        private final Consumer<Task> handler;
        private final TaskCallback callback;

        public Task(Consumer<Task> handler, TaskCallback callback) {
            this.handler = handler;
            this.callback = callback;
        }

        public void reportProgress(String title, long work, long total) {
            this.callback.reportProgress(title, work, total);
        }

        @Override
        public void run() {
            this.handler.accept(this);
        }
    }
}
