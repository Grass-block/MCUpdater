package org.atcgroup.mcupdater.server.v4.data;

import java.util.HashSet;
import java.util.Set;

public final class DiffFileInfo {
    private final Set<String> referenced = new HashSet<>();
    private final String sha1;

    public DiffFileInfo(String sha1) {
        this.sha1 = sha1;
    }

    public String getSha1() {
        return this.sha1;
    }

    public Set<String> getReferenced() {
        return referenced;
    }

    public void addReferenced(String ref) {
        this.referenced.add(ref);
    }

    public void removeReferenced(String ref) {
        this.referenced.remove(ref);
    }

    public boolean hasNoReferences() {
        return this.referenced.isEmpty();
    }
}
