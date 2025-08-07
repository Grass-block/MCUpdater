package org.atcraftmc.updater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Consumer;

public final class P52_CDNUploadObject implements Packet {
    private final byte[] data;
    private final String accessToken;
    private final boolean next;
    private final String name;
    private final String owner;

    @DeserializedConstructor
    public P52_CDNUploadObject(ByteBuf buffer) {
        this.name = BufferUtil.readString(buffer);
        this.owner = BufferUtil.readString(buffer);
        this.accessToken = BufferUtil.readString(buffer);
        this.next = buffer.readBoolean();
        this.data = new byte[buffer.readShort()];
        buffer.readBytes(this.data);
    }

    public P52_CDNUploadObject(String name, String owner, byte[] data, String accessToken, boolean next) {
        this.data = data;
        this.accessToken = accessToken;
        this.next = next;
        this.name = name;
        this.owner = owner;
    }

    public static void read(File file, String owner, String token, Consumer<P52_CDNUploadObject> handler) {
        try (var in = new FileInputStream(file)) {
            var buffer = new byte[8192];
            var len = 0;
            var counter = 0;
            var c2 = 0;

            while ((len = in.read(buffer)) != -1) {
                var data2 = new byte[len];
                System.arraycopy(buffer, 0, data2, 0, len);
                handler.accept(new P52_CDNUploadObject(file.getName(), owner, data2, token, len == 8192));

                counter += len;
                c2++;

                if (c2 > 1024) {
                    System.out.printf("[debug] uploading %s %s/%s%n", file.getName(), counter, file.length());
                    c2 = 0;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getData() {
        return data;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public boolean hasNext() {
        return next;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    @Override
    public void write(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.name);
        BufferUtil.writeString(buffer, this.owner);
        BufferUtil.writeString(buffer, this.accessToken);
        buffer.writeBoolean(this.next);
        buffer.writeShort(this.data.length);
        buffer.writeBytes(this.data);
    }
}
