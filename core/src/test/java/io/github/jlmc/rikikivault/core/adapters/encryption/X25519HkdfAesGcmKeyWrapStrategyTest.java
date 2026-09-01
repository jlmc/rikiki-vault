package io.github.jlmc.rikikivault.core.adapters.encryption;

import io.github.jlmc.rikikivault.core.domain.exception.DecryptionException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class X25519HkdfAesGcmKeyWrapStrategyTest {

    private final X25519HkdfAesGcmKeyWrapStrategy strategy = new X25519HkdfAesGcmKeyWrapStrategy();
    private final SecureRandom random = new SecureRandom();

    private static KeyPair generateX25519KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair();
    }

    private byte[] randomKey() {
        byte[] key = new byte[32];
        random.nextBytes(key);
        return key;
    }

    @Test
    void wrapThenUnwrapReturnsTheOriginalKey() throws Exception {
        KeyPair recipient = generateX25519KeyPair();
        byte[] fileKey = randomKey();

        byte[] wrapped = strategy.wrap(fileKey, recipient.getPublic());
        byte[] unwrapped = strategy.unwrap(wrapped, recipient.getPrivate());

        assertArrayEquals(fileKey, unwrapped);
    }

    @Test
    void unwrapWithWrongPrivateKeyFails() throws Exception {
        KeyPair recipient = generateX25519KeyPair();
        KeyPair attacker = generateX25519KeyPair();
        byte[] fileKey = randomKey();

        byte[] wrapped = strategy.wrap(fileKey, recipient.getPublic());

        assertThrows(DecryptionException.class, () -> strategy.unwrap(wrapped, attacker.getPrivate()));
    }

    @Test
    void repeatedWrapsOfSameKeyProduceDifferentCiphertext() throws Exception {
        KeyPair recipient = generateX25519KeyPair();
        byte[] fileKey = randomKey();

        byte[] first = strategy.wrap(fileKey, recipient.getPublic());
        byte[] second = strategy.wrap(fileKey, recipient.getPublic());

        assertNotEquals(java.util.Base64.getEncoder().encodeToString(first),
                java.util.Base64.getEncoder().encodeToString(second),
                "ephemeral key + nonce randomness must make repeated wraps differ");

        // but both must still unwrap correctly
        assertArrayEquals(fileKey, strategy.unwrap(first, recipient.getPrivate()));
        assertArrayEquals(fileKey, strategy.unwrap(second, recipient.getPrivate()));
    }

    @Test
    void unwrapOfCorruptedBlobFails() throws Exception {
        KeyPair recipient = generateX25519KeyPair();
        byte[] wrapped = strategy.wrap(randomKey(), recipient.getPublic());
        wrapped[wrapped.length - 1] ^= 0x01;

        assertThrows(DecryptionException.class, () -> strategy.unwrap(wrapped, recipient.getPrivate()));
    }
}
