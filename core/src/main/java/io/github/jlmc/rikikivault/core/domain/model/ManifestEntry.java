package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Objects;

/**
 * One tracked file in the vault manifest. {@code path} is the .enc path under {@code documents/},
 * {@code plaintextPath} is the corresponding path under {@code local/}, {@code hash} is the
 * SHA-256 of the plaintext content at the time it was last encrypted.
 */
public record ManifestEntry(String path, String plaintextPath, FileHash hash, String formatVersion) {

    public ManifestEntry {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(plaintextPath, "plaintextPath must not be null");
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(formatVersion, "formatVersion must not be null");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (plaintextPath.isBlank()) {
            throw new IllegalArgumentException("plaintextPath must not be blank");
        }
        if (formatVersion.isBlank()) {
            throw new IllegalArgumentException("formatVersion must not be blank");
        }
    }
}
