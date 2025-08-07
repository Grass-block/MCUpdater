package org.atcraftmc.updater.network.packet;

import me.gb2022.simpnet.packet.Packet;

public interface QueryPacket extends Packet {
    String getQueryId();
}
