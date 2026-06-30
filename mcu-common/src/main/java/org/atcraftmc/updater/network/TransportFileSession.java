package org.atcraftmc.updater.network;

import org.atcraftmc.updater.DiffCheck;
import org.atcraftmc.updater.network.packet.P70_CDNUploadWorkerStart;
import org.atcraftmc.updater.network.packet.P71_CDNUploadWorkerPayload;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class TransportFileSession implements Closeable {
    private final File file;
    private final RandomAccessFile randomAccessFile;
    private final long length;
    private final long totalPackets;
    private final byte[] sha256;
    private final byte[] buffer = new byte[MCUProtocol.CDN_PAYLOAD_SIZE];
    private long currentPacketId = -1;

    public TransportFileSession(File file) throws Exception {
        this.file = file;
        this.randomAccessFile = new RandomAccessFile(file, "rw");
        this.totalPackets = file.length() / MCUProtocol.CDN_PAYLOAD_SIZE;
        this.length = file.length();
        this.sha256 = DiffCheck.calculateSHA256(file);

        CDNUploadWorker.LOGGER.info("Starting CDN upload {} ({} bytes in {} packets)", this.file.getName(), this.length, this.totalPackets);
    }

    @Override
    public void close() throws IOException {
        this.randomAccessFile.close();
    }

    public P70_CDNUploadWorkerStart createHeader(String user, String token) {
        return new P70_CDNUploadWorkerStart(user, token, this.file.getName(), this.sha256, this.length, this.totalPackets);
    }

    public P71_CDNUploadWorkerPayload next() throws IOException {
        this.currentPacketId++;

        var offset = this.currentPacketId * MCUProtocol.CDN_PAYLOAD_SIZE;

        this.randomAccessFile.seek(offset);

        var bytes = this.randomAccessFile.read(this.buffer);

        if (bytes == MCUProtocol.CDN_PAYLOAD_SIZE) {
            return new P71_CDNUploadWorkerPayload(this.currentPacketId, this.buffer);
        }

        var newBuffer = new byte[bytes];

        System.arraycopy(this.buffer, 0, newBuffer, 0, bytes);

        return new P71_CDNUploadWorkerPayload(this.currentPacketId, newBuffer);
    }

    public boolean complete() {
        return this.currentPacketId == this.totalPackets;
    }

    public long getCurrentPacketId() {
        return currentPacketId;
    }

    public File getFile() {
        return this.file;
    }
}
