package org.atcgroup.mcupdater.data;

public final class ServerMeta {
    private final String serverBrand;
    private final String serverVersion;
    private final String sessionId;
    private final boolean hasCDNInfo;
    private final String cdnHost;
    private final int cdnPort;
    private final String cdnRepository;

    public ServerMeta(String serverBrand, String version, String sessionId, boolean hasCDNInfo, String cdnHost, int cdnPort, String cdnRepository) {
        this.serverBrand = serverBrand;
        this.serverVersion = version;
        this.sessionId = sessionId;
        this.hasCDNInfo = hasCDNInfo;
        this.cdnHost = cdnHost;
        this.cdnPort = cdnPort;
        this.cdnRepository = cdnRepository;
    }

    public String getCdnRepository() {
        return cdnRepository;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getCdnPort() {
        return cdnPort;
    }

    public String getCdnHost() {
        return cdnHost;
    }

    public String getServerBrand() {
        return serverBrand;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public boolean hasCDNInfo() {
        return hasCDNInfo;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ServerMeta{");
        sb.append("serverBrand='").append(serverBrand).append('\'');
        sb.append(", serverVersion='").append(serverVersion).append('\'');
        sb.append(", sessionId='").append(sessionId).append('\'');
        sb.append(", hasCDNInfo=").append(hasCDNInfo);
        sb.append(", cdnHost='").append(cdnHost).append('\'');
        sb.append(", cdnPort=").append(cdnPort);
        sb.append('}');
        return sb.toString();
    }
}
