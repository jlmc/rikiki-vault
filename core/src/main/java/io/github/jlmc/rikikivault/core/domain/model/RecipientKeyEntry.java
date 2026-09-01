package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Arrays;
import java.util.Objects;

public record RecipientKeyEntry(KeyFingerprint recipientFingerprint, byte[] wrappedKeyBlob) {

    public RecipientKeyEntry {
        Objects.requireNonNull(recipientFingerprint, "recipientFingerprint must not be null");
        Objects.requireNonNull(wrappedKeyBlob, "wrappedKeyBlob must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipientKeyEntry other)) return false;
        return recipientFingerprint.equals(other.recipientFingerprint) && Arrays.equals(wrappedKeyBlob, other.wrappedKeyBlob);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientFingerprint, Arrays.hashCode(wrappedKeyBlob));
    }

    @Override
    public String toString() {
        return "RecipientKeyEntry[recipientFingerprint=" + recipientFingerprint + ", wrappedKeyBlobLength=" + wrappedKeyBlob.length + "]";
    }
}
