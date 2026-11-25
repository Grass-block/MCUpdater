package org.atcgroup.mcupdater.server.v4;

import io.netty.buffer.Unpooled;
import me.gb2022.mcupdater.UpdateHeader;
import me.gb2022.mcupdater.data.VersionAsset;
import me.gb2022.mcupdater.protocol.P13_VersionHeaders;
import me.gb2022.mcupdater.server.VersionInfo;
import me.gb2022.simpnet.util.BufferUtil;
import org.atcgroup.mcupdater.CompiledVersionData;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VersionInfoCodecTest {
    @Test
    void roundTripsMixedAssetsAndOperations() {
        var version = VersionInfo.fromJson("""
                {"channel":"server","version":"initial","timestamp":1790660497903,
                 "assets":{"local":{"provider":"rtp"},
                           "remote":{"provider":"http","url":"https://example.com/file","length":2147483648}},
                 "extract":["local"],"update":{"/config/test.cfg":"remote"},"delete":["/old"]}
                """);
        assertInstanceOf(VersionAsset.RTPAsset.class, version.getAssets().get("local"));
        var http = assertInstanceOf(VersionAsset.HttpAsset.class, version.getAssets().get("remote"));
        assertEquals("remote", http.getResourceKey());
        assertEquals(2147483648L, http.getLength());
        assertEquals(version.toJson(), VersionInfo.fromJson(version.toJson()).toJson());
        assertHeaderRoundTrip(version);
    }

    @Test
    void roundTripsEmptyAssetsAndOptionalOperations() {
        var version = VersionInfo.fromJson("""
                {"channel":"server","version":"empty","timestamp":0,"assets":{}}
                """);
        assertTrue(version.getAssets().isEmpty());
        assertEquals(version.toJson(), VersionInfo.fromJson(version.toJson()).toJson());
        assertHeaderRoundTrip(version);
    }

    @Test
    void packetRoundTripsUpdateHeader() {
        var version = VersionInfo.fromJson("""
                {"channel":"server","version":"initial","timestamp":1,
                 "assets":{"remote":{"provider":"http","url":"https://example.com/file","length":42}}}
                """);
        var packet = new P13_VersionHeaders(headerFor(version));
        var buffer = Unpooled.buffer();
        try {
            packet.write(buffer);
            var decoded = new P13_VersionHeaders(buffer);
            assertHeader(version, decoded.getHeader());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void copiesAssetsAndRejectsMismatchedKeys() {
        var assets = new LinkedHashMap<String, VersionAsset>();
        assets.put("local", new VersionAsset.RTPAsset("local"));
        var version = new VersionInfo("server", "initial", 0, assets, List.of(), Map.of(), List.of());
        assets.clear();
        assertEquals(1, version.getAssets().size());
        assertThrows(UnsupportedOperationException.class, () -> version.getAssets().clear());
        assertThrows(IllegalArgumentException.class, () -> new VersionInfo("server", "initial", 0,
                Map.of("wrong", new VersionAsset.RTPAsset("local")), List.of(), Map.of(), List.of()));
    }

    @Test
    void rejectsDuplicateAssetKeysAndInvalidCounts() {
        for (int count : new int[] {-1, Integer.MAX_VALUE, 2}) {
            var buffer = Unpooled.buffer();
            try {
                buffer.writeInt(0); // No compiled version summaries before the asset count.
                buffer.writeInt(count);
                new VersionAsset.RTPAsset("duplicate").write(buffer);
                new VersionAsset.RTPAsset("duplicate").write(buffer);
                assertThrows(IllegalArgumentException.class, () -> new UpdateHeader(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    // VersionInfo is the JSON manifest; the binary payload now belongs to UpdateHeader.
    // There is no manifest-to-header builder yet, so construct the wire fixture explicitly.
    private static UpdateHeader headerFor(VersionInfo version) {
        var buffer = Unpooled.buffer();
        try {
            buffer.writeInt(1);
            var summary = new CompiledVersionData(version.getChannel(), "Server", version.getTimestamp());
            summary.addElapsedUpdateLog(version.getVersion(), "Update log");
            summary.write(buffer);
            buffer.writeInt(version.getAssets().size());
            version.getAssets().values().forEach(asset -> asset.write(buffer));
            buffer.writeInt(version.getExtract().size());
            version.getExtract().forEach(value -> BufferUtil.writeString(buffer, value));
            buffer.writeInt(version.getUpdate().size());
            version.getUpdate().forEach((path, key) -> {
                BufferUtil.writeString(buffer, path);
                BufferUtil.writeString(buffer, key);
            });
            buffer.writeInt(version.getDelete().size());
            version.getDelete().forEach(value -> BufferUtil.writeString(buffer, value));
            var header = new UpdateHeader(buffer);
            assertHeader(version, header);
            assertFalse(buffer.isReadable());
            return header;
        } finally {
            buffer.release();
        }
    }

    private static void assertHeader(VersionInfo version, UpdateHeader header) {
        assertEquals(1, header.getVersions().size());
        var summary = header.getVersions().iterator().next();
        assertEquals(version.getChannel(), summary.getChannel());
        assertEquals("Server", summary.getChannelDisplayName());
        assertEquals(version.getTimestamp(), summary.getHeadTimeStamp());
        assertEquals(Map.of(version.getVersion(), "Update log"), summary.getElapsedUpdateLogs());
        assertEquals(version.getAssets().keySet(), header.getAssets().keySet());
        version.getAssets().forEach((key, asset) -> {
            var expected = new com.google.gson.JsonObject();
            var actual = new com.google.gson.JsonObject();
            asset.write(expected);
            var decoded = header.getAssets().get(key);
            assertEquals(key, decoded.getResourceKey());
            decoded.write(actual);
            assertEquals(expected, actual);
        });
        assertEquals(Set.copyOf(version.getExtract()), header.getExtract());
        assertEquals(version.getUpdate(), header.getUpdate());
        assertEquals(Set.copyOf(version.getDelete()), header.getDelete());
    }

    private static void assertHeaderRoundTrip(VersionInfo version) {
        var buffer = Unpooled.buffer();
        try {
            headerFor(version).write(buffer);
            assertHeader(version, new UpdateHeader(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
