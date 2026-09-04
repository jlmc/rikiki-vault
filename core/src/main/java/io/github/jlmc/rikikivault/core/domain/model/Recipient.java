package io.github.jlmc.rikikivault.core.domain.model;

import java.security.PublicKey;
import java.util.Objects;

/**
 * One machine authorized to decrypt this vault's contents. {@code fingerprint} is
 * derived from {@code publicKey} ({@link KeyFingerprint#of(PublicKey)}) and is what
 * {@code RecipientKeyEntry}/authorization checks match against.
 */
public record Recipient(String label, KeyFingerprint fingerprint, PublicKey publicKey) {

    public Recipient {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
