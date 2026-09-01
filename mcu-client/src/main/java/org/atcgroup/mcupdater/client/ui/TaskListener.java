package org.atcgroup.mcupdater.client.ui;

public interface TaskListener {
    void setProgress(int progress);

    void setProgressTitle(String title);

    void setUnsureProgress(String unsureProgress);
}
