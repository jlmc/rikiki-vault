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

    /**
     * Cross-checked byte-for-byte against OpenSSL 3.x for the same inputs:
     * {@code openssl kdf -keylen 32 -kdfopt digest:SHA2-256 -kdfopt pass:"correct horse battery staple"
     * -kdfopt hexsalt:30313233343536373839616263646566 -kdfopt iter:10000 PBKDF2} - this is what the
     * recovery scripts under {@code docs/faq/scripts/} rely on to unwrap a passphrase-protected
     * {@code private.key} without the app, so it's worth pinning as a regression test: if this ever
     * stops matching (e.g. a future change to how the passphrase is turned into bytes), those
     * scripts would silently derive the wrong key.
     */
    @Test
    void matchesOpenSslPbkdf2ForTheSameInputs() {
        byte[] key = Pbkdf2.deriveKey("correct horse battery staple".toCharArray(), SALT_A, 10_000, 256);

        assertEquals(
                "56A384014AD36808CC0FAB21F7526F986B5B0FE60D1A75CD31B7C80F1823D3DE",
                bytesToHex(key));
    }

    /** Confirms non-ASCII passphrases (UTF-8 multibyte, including a 4-byte emoji) also match OpenSSL. */
    @Test
    void matchesOpenSslPbkdf2ForANonAsciiPassphrase() {
        byte[] key = Pbkdf2.deriveKey("café senha çãõü€🔒".toCharArray(), SALT_A, 5_000, 256);

        assertEquals(
                "611F9F72B89F6C40EDA6ACE196C685E6555D88AC551C2F94030A0BF2DB80DA7F",
                bytesToHex(key));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
