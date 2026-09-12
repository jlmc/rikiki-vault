package io.github.jlmc.rikikivault.core.domain.model;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;

/**
 * {@code hmacKey} is the per-vault secret used to compute every entry's {@link ManifestEntry#hash}
 * ({@link FileHash#hmac}) - it lives here, inside the manifest, rather than in a separate file,
 * because the manifest itself is only ever readable by the same audience (current recipients) that
 * should be able to use this key: access to one already implies access to the other.
 */
public record VaultManifest(int version, byte[] hmacKey, List<ManifestEntry> files) {

    public static final int HMAC_KEY_LENGTH = 32;

    public VaultManifest {
        Objects.requireNonNull(hmacKey, "hmacKey must not be null");
        Objects.requireNonNull(files, "files must not be null");
        if (hmacKey.length != HMAC_KEY_LENGTH) {
            throw new IllegalArgumentException("hmacKey must be " + HMAC_KEY_LENGTH + " bytes, got " + hmacKey.length);
        }
        files = List.copyOf(files);
    }

    public static VaultManifest empty() {
        return new VaultManifest(1, generateHmacKey(), List.of());
    }

    public static byte[] generateHmacKey() {
        byte[] key = new byte[HMAC_KEY_LENGTH];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VaultManifest other)) return false;
        return version == other.version
                && java.util.Arrays.equals(hmacKey, other.hmacKey)
                && files.equals(other.files);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(version, files) + java.util.Arrays.hashCode(hmacKey);
    }

    @Override
    public String toString() {
        return "VaultManifest[version=" + version + ", hmacKey=<redacted>, files=" + files + "]";
    }
}
