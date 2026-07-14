package org.atcraftmc.updater.client.ui.framework;

import javax.swing.*;

public abstract class UI<I extends UI<I>> {
    private UIHandle<I> handle;

    public void setHandle(UIHandle<I> handle) {
        this.handle = handle;
    }

    public UIHandle<I> handle() {
        return handle;
    }

    public abstract void build(JFrame frame);

    public JPanel getRootPanel() {
        return null;
    }

    public void setup(UIHandle<I> handle) {
    }
}
