package org.atcgroup.mcupdater.server.v4.data;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.util.BufferUtil;

public final class ChannelMetadata {
    private final String channel;
    private final boolean enforced;
    private final TranslatableComponent name;
    private final TranslatableComponent description;

    public ChannelMetadata(String channel, boolean enforced, TranslatableComponent name, TranslatableComponent description) {
        this.channel = channel;
        this.enforced = enforced;
        this.name = name;
        this.description = description;
    }

    public ChannelMetadata(ByteBuf buffer) {
        this.channel = BufferUtil.readString(buffer);
        this.enforced = buffer.readBoolean();
        this.name = new TranslatableComponent(buffer);
        this.description = new TranslatableComponent(buffer);
    }

    public String getChannel() {
        return channel;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public TranslatableComponent getDescription() {
        return description;
    }

    public TranslatableComponent getName() {
        return name;
    }

    public void encode(ByteBuf buffer) {
        BufferUtil.writeString(buffer, this.channel);
        buffer.writeBoolean(this.enforced);
        this.name.encode(buffer);
        this.description.encode(buffer);
    }
}
