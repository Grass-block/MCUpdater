package org.atcgroup.mcupdater.server.v4.storage;

import java.util.Optional;

public final class CASObjectMeta {
    public static final String NO_DOWNLOAD_LINK = "_";

    private final long length;
    private final String sha1;
    private final String sha512;
    private final String sourceLink;

    public CASObjectMeta(long length, String sha1, String sha512, String sourceLink) {
        this.length = length;
        this.sha1 = sha1;
        this.sha512 = sha512;
        this.sourceLink = sourceLink;
    }

    public CASObjectMeta(long length, String sha1, String sha512) {
        this.length = length;
        this.sha1 = sha1;
        this.sha512 = sha512;
        this.sourceLink = NO_DOWNLOAD_LINK;
    }

    public String getSha1() {
        return sha1;
    }

    public String getSha512() {
        return sha512;
    }

    public long getLength() {
        return length;
    }

    public Optional<String> getSourceLink() {
        if(this.sourceLink.equals(NO_DOWNLOAD_LINK)) {
            return Optional.empty();
        }

        return Optional.of(this.sourceLink);
    }

    public boolean existSourceLink() {
        return !this.sourceLink.equals(NO_DOWNLOAD_LINK);
    }
}
