package io.github.jlmc.core.configuration;

public record EncryptionSettings(int aesKeyBits) {

    private static final int DEFAULT_AES_KEY_BITS = 256;

    public EncryptionSettings {
        if (aesKeyBits != 128 && aesKeyBits != 192 && aesKeyBits != 256) {
            throw new IllegalArgumentException("aesKeyBits must be one of 128, 192, 256, got " + aesKeyBits);
        }
    }

    public static EncryptionSettings defaults() {
        return new EncryptionSettings(DEFAULT_AES_KEY_BITS);
    }
}
