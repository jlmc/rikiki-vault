package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Objects;

/**
 * A detected difference between the local plaintext tree and the manifest. {@code path} is
 * always the plaintext-relative path (under {@code local/}), matching how the CLI status
 * example in Plan.md §27 reports changes.
 */
public record VaultChange(ChangeType type, String path) {

    public VaultChange {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
    }

    public enum ChangeType {
        ADDED, MODIFIED, DELETED
    }
}
