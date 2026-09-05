package io.github.jlmc.rikikivault.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a {@code restore}: which manifest entries were (re)decrypted into {@code local/},
 * which already existed there and were left alone (no {@code force}), and which this machine's
 * identity isn't authorized to decrypt (e.g. a revoked machine restoring against a manifest
 * re-encrypted after it lost access).
 */
public record RestoreLocalFilesResult(
        List<String> restoredPaths, List<String> skippedPaths, List<String> unauthorizedPaths) {

    public RestoreLocalFilesResult {
        Objects.requireNonNull(restoredPaths, "restoredPaths must not be null");
        Objects.requireNonNull(skippedPaths, "skippedPaths must not be null");
        Objects.requireNonNull(unauthorizedPaths, "unauthorizedPaths must not be null");
        restoredPaths = List.copyOf(restoredPaths);
        skippedPaths = List.copyOf(skippedPaths);
        unauthorizedPaths = List.copyOf(unauthorizedPaths);
    }
}
