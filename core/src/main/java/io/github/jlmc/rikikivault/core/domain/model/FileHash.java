package io.github.jlmc.rikikivault.core.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record FileHash(String hex) {

    public FileHash {
        Objects.requireNonNull(hex, "hex must not be null");
        if (hex.length() != 64) {
            throw new IllegalArgumentException("A FileHash hex must be 64 characters (SHA-256), got " + hex.length());
        }
    }

    public static FileHash of(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        return ofBytes(sha256(content));
    }

    public static FileHash ofBytes(byte[] rawHash) {
        Objects.requireNonNull(rawHash, "rawHash must not be null");
        if (rawHash.length != 32) {
            throw new IllegalArgumentException("Expected 32 raw bytes (SHA-256), got " + rawHash.length);
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

    @Override
    public String toString() {
        return hex;
    }
}
