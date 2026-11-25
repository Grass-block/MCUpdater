package me.gb2022.mcupdater.client.util;

import me.gb2022.mcupdater.client.ui.ClientUI;

public abstract class TaskCallback {
    protected ClientUI ui;

    public abstract void reportProgress(String title, long work, long total);
    public void cleanup(){}

    public void __init(ClientUI context){
        this.ui = context;
    }

    static TaskCallback asyncTask(String uuid){
        return new TaskCallback() {
            @Override
            public void reportProgress(String title, long work, long total) {
                ui.handleSubProgressReport(uuid,title,work,total);
            }

            @Override
            public void cleanup() {
                ui.removeSubProgressReport(uuid);
            }
        };
    }

    static TaskCallback mainTask(){
        return new TaskCallback() {
            @Override
            public void reportProgress(String title, long work, long total) {
                this.ui.handleMainProgressReport(title,work,total);
            }
        };
    }
}
