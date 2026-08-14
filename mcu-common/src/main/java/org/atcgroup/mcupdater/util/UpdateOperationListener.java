package org.atcgroup.mcupdater.util;

public interface UpdateOperationListener {
    void setProgress(int prog);

    void setCommentMessage(String msg);
}
