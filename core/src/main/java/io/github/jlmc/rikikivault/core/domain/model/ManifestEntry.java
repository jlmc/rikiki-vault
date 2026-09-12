package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Objects;

/**
 * One tracked file in the vault manifest. {@code id} is an opaque, randomly generated identifier
 * (never derived from the path - that would let an offline attacker test candidate filenames)
 * that stays stable across re-publishes of the same file and names its ciphertext blob under
 * {@code documents/} (see {@link #documentsRelativePath()}). {@code plaintextPath} is the real
 * path under {@code local/} - safe to keep as plain text here because the *file* holding this
 * record ({@code manifest.json}) is itself encrypted as a whole, not this individual field.
 * {@code hash} is the HMAC-SHA256 (keyed by {@link VaultManifest#hmacKey()}) of the plaintext
 * content at the time it was last encrypted.
 */
public record ManifestEntry(String id, String plaintextPath, FileHash hash, String formatVersion) {

    public ManifestEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(plaintextPath, "plaintextPath must not be null");
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(formatVersion, "formatVersion must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (plaintextPath.isBlank()) {
            throw new IllegalArgumentException("plaintextPath must not be blank");
        }
        if (formatVersion.isBlank()) {
            throw new IllegalArgumentException("formatVersion must not be blank");
        }
    }

    /** The opaque, path-blind location of this entry's ciphertext under {@code documents/}. */
    public String documentsRelativePath() {
        return id + ".enc";
    }
}
