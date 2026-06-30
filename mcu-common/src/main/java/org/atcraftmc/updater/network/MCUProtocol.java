package org.atcraftmc.updater.network;

import me.gb2022.simpnet.channel.NettyChannelInitializer;
import me.gb2022.simpnet.packet.PacketRegistry;
import org.atcraftmc.updater.network.packet.*;

public interface MCUProtocol {
    PacketRegistry PACKETS = new PacketRegistry(512, (i) -> {
        //client-server conversation
        i.register(0x00, P00_KeepAlive.class);
        i.register(0x02, P02_ClientConversationEnd.class);
        i.register(0x0F, P0F_ServerProgressUpdate.class);

        //update infos
        i.register(0x10, P10_VersionInfo.class);
        i.register(0x11, P11_FileExpand.class);
        i.register(0x12, P12_FileDelete.class);
        i.register(0x13, P13_PatchFileInfo.class);
        i.register(0x14, P14_PatchFileSlice.class);
        i.register(0x15, P15_CDNDownloads.class);
        i.register(0x16, P16_CDNDownloadComplete.class);
        i.register(0x1F, P1F_UpdateProgressPredict.class);

        //queries
        i.register(0x20, P20_VersionLogRequest.class);
        i.register(0x21, P21_VersionLogInfo.class);
        i.register(0x22, P22_UpdateChannelDataRequest.class);
        i.register(0x23, P23_UpdateChannelList.class);

        //CDN access
        i.register(0x50, P50_CDNCheckObjectStatus.class);
        i.register(0x51, P51_CDNObjectState.class);
        i.register(0x52, P52_CDNUploadObject.class);
        i.register(0x53, P53_CDNDownloadRequest.class);
        i.register(0x54, P54_CDNFileChangeAttempt.class);
        i.register(0x55, P55_CDNFileChangeReady.class);
    });
    int CDN_PAYLOAD_SIZE = 256*1024;//256KiB

    static NettyChannelInitializer initializer() {
        return new NettyChannelInitializer().config((i) -> {
            i.lengthFrame();
            i.compression(1024, 1);
            i.packet(PACKETS);
        });
    }
}
