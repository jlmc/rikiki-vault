package io.github.jlmc.rikikivault.core.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.HexFormat;
import java.util.Objects;

public record KeyFingerprint(String hex) {

    public KeyFingerprint {
        Objects.requireNonNull(hex, "hex must not be null");
        if (hex.length() != 64) {
            throw new IllegalArgumentException("A KeyFingerprint hex must be 64 characters (SHA-256), got " + hex.length());
        }
    }

    public static KeyFingerprint of(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        return ofBytes(sha256(publicKey.getEncoded()));
    }

    public static KeyFingerprint ofBytes(byte[] rawFingerprint) {
        Objects.requireNonNull(rawFingerprint, "rawFingerprint must not be null");
        if (rawFingerprint.length != 32) {
            throw new IllegalArgumentException("Expected 32 raw bytes (SHA-256), got " + rawFingerprint.length);
        }
        return new KeyFingerprint(HexFormat.of().formatHex(rawFingerprint));
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

    @Override
    public String toString() {
        return hex;
    }
}
