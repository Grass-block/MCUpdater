package org.atcraftmc.updater.network.packet;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcraftmc.updater.data.UpdateChannelMeta;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class P23_UpdateChannelList implements Packet {
    private final Set<UpdateChannelMeta> metas = new HashSet<>();

    @DeserializedConstructor
    public P23_UpdateChannelList(ByteBuf buffer) {
        var length = buffer.readInt();
        for (int i = 0; i < length; i++) {
            this.metas.add(new UpdateChannelMeta(JsonParser.parseString(BufferUtil.readString(buffer)).getAsJsonObject()));
        }
    }

    public P23_UpdateChannelList(Collection<UpdateChannelMeta> metas) {
        this.metas.addAll(metas);
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.metas.size());

        for (var meta : this.metas) {
            BufferUtil.writeString(buffer, meta.json().toString());
        }
    }

    public Set<UpdateChannelMeta> getMetas() {
        return metas;
    }
}
