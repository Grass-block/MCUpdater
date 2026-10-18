package org.atcgroup.mcupdater.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Pipeline<I> {
    protected final Map<String, I> processors = new HashMap<>();
    protected final List<String> orderList = new ArrayList<>();

    public Pipeline() {
    }

    public void update() {
    }

    public void addLast(String id, I item) {
        if (this.processors.containsKey(id)) {
            return;
        }

        this.processors.put(id, item);
        this.orderList.add(id);
        this.update();
    }

    public void addFirst(String id, I item) {
        if (this.processors.containsKey(id)) {
            return;
        }

        this.processors.put(id, item);
        this.orderList.add(0, id);
        this.update();
    }

    private void _insert(String target, String id, int offset, I item) {
        if (this.processors.containsKey(id)) {
        }

        if (!this.processors.containsKey(target)) {
            throw new IllegalArgumentException("Unknown target: " + target);
        } else {
            this.processors.put(id, item);
            this.orderList.add(this.orderList.indexOf(target) + offset, id);
            this.update();
        }
    }

    public void addBefore(String target, String id, I item) {
        this._insert(target, id, -1, item);
    }

    public void addAfter(String target, String id, I item) {
        this._insert(target, id, 1, item);
    }

    public void remove(String id) {
        this.processors.remove(id);
        this.orderList.remove(id);
        this.update();
    }

    public List<I> list() {
        var result = new ArrayList<I>();

        for (String id : this.orderList) {
            result.add(this.processors.get(id));
        }

        return result;
    }

    public void clear() {
        this.processors.clear();
        this.orderList.clear();
        this.update();
    }
}