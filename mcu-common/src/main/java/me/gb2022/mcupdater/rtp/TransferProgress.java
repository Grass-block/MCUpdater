package me.gb2022.mcupdater.rtp;

/** Bytes mean successful writes; completedFiles means SHA-1 verified and committed files. */
public record TransferProgress(long receivedBytes, long totalBytes, int completedFiles, int totalFiles,
                               java.util.List<String> missingIds) {
    public TransferProgress {
        missingIds = java.util.List.copyOf(missingIds);
    }

    public TransferProgress(long receivedBytes, long totalBytes, int completedFiles, int totalFiles) {
        this(receivedBytes, totalBytes, completedFiles, totalFiles, java.util.List.of());
    }
    public boolean completed() {
        return this.completedFiles == this.totalFiles;
    }
}
