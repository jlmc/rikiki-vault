package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * A passphrase-protected {@code private.key} file, decoded from (or ready to be encoded into)
 * the on-disk "RVPK" envelope format. Purely a data holder - the actual PBKDF2 derivation and
 * AES-GCM sealing/opening of {@link #ciphertext()} happen in {@code LocalKeyStoreAdapter}.
 */
public record PrivateKeyEnvelope(
        int kdfId,
        int iterations,
        byte[] salt,
        byte[] nonce,
        byte[] ciphertext
) {

    public PrivateKeyEnvelope {
        Objects.requireNonNull(salt, "salt must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrivateKeyEnvelope other)) return false;
        return kdfId == other.kdfId
                && iterations == other.iterations
                && Arrays.equals(salt, other.salt)
                && Arrays.equals(nonce, other.nonce)
                && Arrays.equals(ciphertext, other.ciphertext);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(kdfId, iterations);
        result = 31 * result + Arrays.hashCode(salt);
        result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(ciphertext);
        return result;
    }

    @Override
    public String toString() {
        return "PrivateKeyEnvelope[kdfId=" + kdfId + ", iterations=" + iterations
                + ", saltLength=" + salt.length + ", ciphertextLength=" + ciphertext.length + "]";
    }
}
