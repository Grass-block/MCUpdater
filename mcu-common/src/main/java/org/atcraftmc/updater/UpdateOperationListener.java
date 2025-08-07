package org.atcraftmc.updater;

public interface UpdateOperationListener {
    void setProgress(int prog);

    void setCommentMessage(String msg);
}
