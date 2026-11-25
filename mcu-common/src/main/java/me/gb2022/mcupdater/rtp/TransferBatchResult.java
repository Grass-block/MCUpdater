package me.gb2022.mcupdater.rtp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record TransferBatchResult(Map<String, Path> files, List<String> missingIds) {
    public TransferBatchResult {
        files = Map.copyOf(files);
        missingIds = List.copyOf(missingIds);
    }
}
