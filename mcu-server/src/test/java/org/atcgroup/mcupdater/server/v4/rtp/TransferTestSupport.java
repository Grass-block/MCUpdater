package org.atcgroup.mcupdater.server.v4.rtp;

import org.atcgroup.mcupdater.server.v4.rtp.TransferFileReceiver;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TransferTestSupport {
    static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final int MTU = TransferFileReceiver.FILE_MTU;

    private TransferTestSupport() {
    }

    static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }

    static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /** Holds writes until the test explicitly runs them; no sleeps or disk timing assumptions. */
    static final class ManualExecutor implements Executor {
        private final Queue<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            this.work.add(command);
        }

        int size() {
            return this.work.size();
        }

        void runNext() {
            var command = this.work.poll();
            assertNotNull(command, "Expected a queued disk operation");
            command.run();
        }
    }
}
