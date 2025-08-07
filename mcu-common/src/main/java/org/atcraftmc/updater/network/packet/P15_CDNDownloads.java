package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashSet;
import java.util.Set;

public final class P15_CDNDownloads implements Packet {
    private final String sessionId;
    private final String host;
    private final int port;
    private final String repo;
    private final Set<String> targets;

    public P15_CDNDownloads(String sessionId, String host, int port, String repo, Set<String> targets) {
        this.sessionId = sessionId;
        this.host = host;
        this.port = port;
        this.repo = repo;
        this.targets = targets;
    }

    @DeserializedConstructor
    public P15_CDNDownloads(ByteBuf buffer) {
        this.sessionId = BufferUtil.readString(buffer);
        this.host = BufferUtil.readString(buffer);
        this.port = buffer.readInt();
        this.repo = BufferUtil.readString(buffer);
        this.targets = new HashSet<>();
        var len = buffer.readShort();

        for (var i = 0; i < len; i++) {
            this.targets.add(BufferUtil.readString(buffer));
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.sessionId);
        BufferUtil.writeString(buffer, this.host);
        buffer.writeInt(this.port);
        BufferUtil.writeString(buffer, this.repo);
        buffer.writeShort(this.targets.size());
        for (var target : this.targets) {
            BufferUtil.writeString(buffer, target);
        }
    }

    public String getRepo() {
        return repo;
    }

    public Set<String> getTargets() {
        return targets;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }
}
