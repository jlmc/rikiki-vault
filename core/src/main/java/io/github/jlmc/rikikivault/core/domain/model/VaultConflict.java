package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Objects;

/**
 * A path that changed both locally (since the last sync) and remotely (in the same {@code pull}),
 * per Plan.md §11/§32 - the file was never touched, this only reports the fact.
 */
public record VaultConflict(String plaintextPath, VaultChange.ChangeType localChangeType, VaultChange.ChangeType remoteChangeType) {

    public VaultConflict {
        Objects.requireNonNull(plaintextPath, "plaintextPath must not be null");
        Objects.requireNonNull(localChangeType, "localChangeType must not be null");
        Objects.requireNonNull(remoteChangeType, "remoteChangeType must not be null");
        if (plaintextPath.isBlank()) {
            throw new IllegalArgumentException("plaintextPath must not be blank");
        }
    }
}
