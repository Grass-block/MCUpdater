package org.atcgroup.mcupdater.network.packet;

import io.netty.buffer.ByteBuf;
import me.gb2022.simpnet.packet.DeserializedConstructor;
import me.gb2022.simpnet.packet.Packet;
import me.gb2022.simpnet.util.BufferUtil;

import java.util.HashMap;
import java.util.Map;

public final class P22_UpdateLogs implements Packet {
    private final Map<String, Map<String, String>> logs;
    private final Map<String, String> channelDisplayNames;

    public P22_UpdateLogs() {
        this.logs = new HashMap<>();
        this.channelDisplayNames = new HashMap<>();
    }

    @DeserializedConstructor
    public P22_UpdateLogs(ByteBuf buffer) {
        this.logs = new HashMap<>();
        this.channelDisplayNames = new HashMap<>();

        var length = buffer.readInt();

        for (var i = 0; i < length; i++) {
            var channel = BufferUtil.readString(buffer);
            var items = buffer.readShort();

            for (var j = 0; j < items; j++) {
                addLog(channel, BufferUtil.readString(buffer), BufferUtil.readString(buffer));
            }
        }

        var length2 = buffer.readInt();
        for (var i = 0; i < length2; i++) {
            var channel = BufferUtil.readString(buffer);
            var displayName = BufferUtil.readString(buffer);
            addChannelDisplayName(channel, displayName);
        }
    }

    public static P22_UpdateLogs sample() {
        var packet = new P22_UpdateLogs();

        packet.addChannelDisplayName("server", "服务端资源(默认)");
        packet.addChannelDisplayName("client-enforced", "客户端资源(默认)");
        packet.addChannelDisplayName("client-optional", "客户端资源(选配)");

        packet.addLog("server", "2.3.8", sampleServerLogV238());
        packet.addLog("server", "2.4.0-sn1", sampleServerLogV240());
        packet.addLog("client-enforced", "1.0.5", sampleClientLog());
        packet.addLog("client-optional", "0.9.1", sampleOptionalLog());

        return packet;
    }

    private static String sampleServerLogV238() {
        return """
                - 更新ProjectE模组
                - 下调部分金属的EMC值
                - 新增部分优化mod
                """;
    }

    private static String sampleServerLogV240() {
        return """
                - 略微降低了MSPT占用。
                - 默认渲染距离调整为 12
                - 调整了键位映射，与原版冲突键位已清除
                - 材质包由 32x 升级至 64x
                """;
    }

    private static String sampleClientLog() {
        return """
                - 新增光影包支持，内置 BSL v8.2 配置
                - 新增按键绑定预设，F6 一键切换视角
                - 新增迷你地图，支持死亡地点标记
                - 修复低配电脑卡顿问题
                - 修复加载世界时偶发崩溃的问题
                - 修复联机时物品栏不同步的问题
                """;
    }

    private static String sampleOptionalLog() {
        return """
                - 添加原版高清材质
                - 添加部分模组的中文汉化包
                - 添加了新的光影组件。
                """;
    }

    public void addLog(String channel, String version, String log) {
        this.logs.computeIfAbsent(channel, k -> new HashMap<>()).put(version, log);
    }

    public void addChannelDisplayName(String channel, String displayName) {
        this.channelDisplayNames.put(channel, displayName);
    }

    public Map<String, String> getChannelDisplayNames() {
        return channelDisplayNames;
    }

    public Map<String, Map<String, String>> getLogs() {
        return logs;
    }

    @Override
    public void write(ByteBuf buffer) {
        buffer.writeInt(this.logs.size());

        for (var channel : this.logs.keySet()) {
            BufferUtil.writeString(buffer, channel);
            var items = this.logs.get(channel);
            buffer.writeShort(items.size());

            for (var entry : items.entrySet()) {
                BufferUtil.writeString(buffer, entry.getKey());
                BufferUtil.writeString(buffer, entry.getValue());
            }
        }

        buffer.writeInt(this.channelDisplayNames.size());
        for (var entry : this.channelDisplayNames.entrySet()) {
            BufferUtil.writeString(buffer, entry.getKey());
            BufferUtil.writeString(buffer, entry.getValue());
        }
    }
}
