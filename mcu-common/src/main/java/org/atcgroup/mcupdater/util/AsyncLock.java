package org.atcgroup.mcupdater.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class AsyncLock {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = this.lock.newCondition();
    private final AtomicBoolean waiting = new AtomicBoolean(false);

    public void pause() {
        this.waiting.set(true);
    }

    public void resume() {
        this.waiting.set(false);
        lock.lock();
        try {
            this.condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void monitor() {
        this.lock.lock();
        try {
            while (this.waiting.get()) {
                try {
                    this.condition.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    public boolean paused() {
        return this.waiting.get();
    }
}
