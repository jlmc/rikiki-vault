package io.github.jlmc.rikikivault.core.adapters.encryption;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Pbkdf2Test {

    private static final byte[] SALT_A = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SALT_B = "fedcba9876543210".getBytes(StandardCharsets.US_ASCII);

    @Test
    void sameInputsProduceTheSameKey() {
        byte[] a = Pbkdf2.deriveKey("correct horse battery staple".toCharArray(), SALT_A, 10_000, 256);
        byte[] b = Pbkdf2.deriveKey("correct horse battery staple".toCharArray(), SALT_A, 10_000, 256);

        assertArrayEquals(a, b);
    }

    @Test
    void differentPassphrasesProduceDifferentKeys() {
        byte[] a = Pbkdf2.deriveKey("correct horse battery staple".toCharArray(), SALT_A, 10_000, 256);
        byte[] b = Pbkdf2.deriveKey("wrong horse battery staple".toCharArray(), SALT_A, 10_000, 256);

        assertFalse(Arrays.equals(a, b));
    }

    @Test
    void differentSaltsProduceDifferentKeys() {
        byte[] a = Pbkdf2.deriveKey("correct horse battery staple".toCharArray(), SALT_A, 10_000, 256);
        byte[] b = Pbkdf2.deriveKey("correct horse battery staple".toCharArray(), SALT_B, 10_000, 256);

        assertFalse(Arrays.equals(a, b));
    }

    @Test
    void keyLengthMatchesRequestedBits() {
        byte[] key128 = Pbkdf2.deriveKey("passphrase".toCharArray(), SALT_A, 10_000, 128);
        byte[] key256 = Pbkdf2.deriveKey("passphrase".toCharArray(), SALT_A, 10_000, 256);

        assertEquals(16, key128.length);
        assertEquals(32, key256.length);
    }
}
