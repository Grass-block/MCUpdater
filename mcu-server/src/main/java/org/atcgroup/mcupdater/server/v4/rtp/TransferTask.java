package org.atcgroup.mcupdater.server.v4.rtp;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.concurrent.CompletionStage;

/** Channel-independent receive task contract, implemented by TransferFileReceiver. */
public interface TransferTask {
    enum State {
        RECEIVING, VERIFYING, COMMITTING, COMPLETED, CANCELLED, TIMED_OUT, FAILED
    }

    String getTaskId();

    Path getFile();

    TransferMode getMode();

    State getState();

    int getReceivedParts();

    BitSet getReceivedPartsSnapshot();

    CompletionStage<Path> completion();
}
