package io.github.jlmc.rikikivault.core.domain.model;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * {@link #hmac} is keyed by a per-vault secret ({@link VaultManifest#hmacKey()}) so that, unlike
 * plain {@link #of}, no one without that key can confirm offline whether a candidate file's content
 * matches a hash they can already see in a (possibly leaked) manifest.
 */
public record FileHash(String hex) {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public FileHash {
        Objects.requireNonNull(hex, "hex must not be null");
        if (hex.length() != 64) {
            throw new IllegalArgumentException("A FileHash hex must be 64 characters (SHA-256/HMAC-SHA256), got " + hex.length());
        }
    }

    public static FileHash of(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        return ofBytes(sha256(content));
    }

    public static FileHash hmac(byte[] key, byte[] content) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(content, "content must not be null");
        return ofBytes(hmacSha256(key, content));
    }

    public static FileHash ofBytes(byte[] rawHash) {
        Objects.requireNonNull(rawHash, "rawHash must not be null");
        if (rawHash.length != 32) {
            throw new IllegalArgumentException("Expected 32 raw bytes (SHA-256/HMAC-SHA256), got " + rawHash.length);
        }
        return new FileHash(HexFormat.of().formatHex(rawHash));
    }

    public byte[] toBytes() {
        return HexFormat.of().parseHex(hex);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on any JVM", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(input);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(HMAC_ALGORITHM + " must be available on any JVM", e);
        }
    }

    @Override
    public String toString() {
        return hex;
    }
}
