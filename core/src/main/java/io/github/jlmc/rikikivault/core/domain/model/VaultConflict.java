package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Objects;

/**
 * A path that changed both locally (since the last sync) and remotely (in the same {@code pull}) -
 * the file was never touched, this only reports the fact. {@code localHash}/
 * {@code remoteHash} are {@code null} when that side is a deletion (no content left to hash).
 */
public record VaultConflict(
        String plaintextPath,
        VaultChange.ChangeType localChangeType,
        VaultChange.ChangeType remoteChangeType,
        FileHash localHash,
        FileHash remoteHash) {

    public VaultConflict {
        Objects.requireNonNull(plaintextPath, "plaintextPath must not be null");
        Objects.requireNonNull(localChangeType, "localChangeType must not be null");
        Objects.requireNonNull(remoteChangeType, "remoteChangeType must not be null");
        if (plaintextPath.isBlank()) {
            throw new IllegalArgumentException("plaintextPath must not be blank");
        }
    }
}
