package me.gb2022.mcupdater.protocol;

import io.netty.buffer.ByteBuf;
import me.gb2022.mcupdater.rtp.protocol.RTPProtocol;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class P32_CASInventorySync implements Packet {
    public static final int MAX_OBJECTS = 200_000;
    public record ObjectInfo(String sha1, long size) {
        public ObjectInfo {
            RTPProtocol.validateSha1(sha1);
            if (size < 0) throw new IllegalArgumentException("Negative CAS object size");
        }
    }

    private final List<ObjectInfo> objects;

    public P32_CASInventorySync(List<ObjectInfo> objects) {
        if (objects.size() > MAX_OBJECTS || objects.stream().map(ObjectInfo::sha1).distinct().count() != objects.size()) {
            throw new IllegalArgumentException("Invalid CAS inventory size or duplicate IDs");
        }
        this.objects = List.copyOf(objects);
    }

    @DeserializedConstructor
    public P32_CASInventorySync(ByteBuf buffer) {
        this(read(buffer));
        if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected inventory payload");
    }

    private static List<ObjectInfo> read(ByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > MAX_OBJECTS) throw new IllegalArgumentException("Invalid inventory count");
        var result = new ArrayList<ObjectInfo>(count);
        var ids = new HashSet<String>();
        for (int i = 0; i < count; i++) {
            var object = new ObjectInfo(BufferUtil.readString(buffer), buffer.readLong());
            if (!ids.add(object.sha1())) throw new IllegalArgumentException("Duplicate CAS object");
            result.add(object);
        }
        return result;
    }

    @Override public void write(ByteBuf buffer) {
        buffer.writeInt(objects.size());
        for (var object : objects) {
            BufferUtil.writeString(buffer, object.sha1());
            buffer.writeLong(object.size());
        }
    }

    public List<ObjectInfo> objects() { return objects; }
}
