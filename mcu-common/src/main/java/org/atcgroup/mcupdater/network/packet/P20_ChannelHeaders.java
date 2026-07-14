package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.data.UpdateChannelMeta;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class P20_ChannelHeaders implements Packet {
    private final Set<UpdateChannelMeta> metas = new HashSet<>();

    @DeserializedConstructor
    public P20_ChannelHeaders(ByteBuf buffer) {
        var length = buffer.readInt();
        for (int i = 0; i < length; i++) {
            var id = BufferUtil.readString(buffer);
            var name = BufferUtil.readString(buffer);
            var description = BufferUtil.readString(buffer);
            var required = buffer.readBoolean();

            this.metas.add(new UpdateChannelMeta(id, name, description, required));
        }
    }

    public P20_ChannelHeaders(Collection<UpdateChannelMeta> metas) {
        this.metas.addAll(metas);
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.metas.size());

        for (var meta : this.metas) {
            BufferUtil.writeString(buffer, meta.id());
            BufferUtil.writeString(buffer, meta.name());
            BufferUtil.writeString(buffer, meta.desc());
            buffer.writeBoolean(meta.required());
        }
    }

    public Set<UpdateChannelMeta> getMetas() {
        return metas;
    }
}
