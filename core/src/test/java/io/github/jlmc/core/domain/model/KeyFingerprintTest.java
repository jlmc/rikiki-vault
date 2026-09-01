package io.github.jlmc.core.domain.model;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyFingerprintTest {

    private static KeyPair generateX25519KeyPair() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("X25519").generateKeyPair();
    }

    @Test
    void sameKeyProducesSameFingerprint() throws Exception {
        KeyPair keyPair = generateX25519KeyPair();

        KeyFingerprint first = KeyFingerprint.of(keyPair.getPublic());
        KeyFingerprint second = KeyFingerprint.of(keyPair.getPublic());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void differentKeysProduceDifferentFingerprints() throws Exception {
        KeyFingerprint first = KeyFingerprint.of(generateX25519KeyPair().getPublic());
        KeyFingerprint second = KeyFingerprint.of(generateX25519KeyPair().getPublic());

        assertNotEquals(first, second);
    }

    @Test
    void hexMustBeExactlySha256Length() {
        assertThrows(IllegalArgumentException.class, () -> new KeyFingerprint("abc"));
    }

    @Test
    void toBytesAndOfBytesRoundTrip() throws Exception {
        KeyFingerprint fingerprint = KeyFingerprint.of(generateX25519KeyPair().getPublic());

        byte[] raw = fingerprint.toBytes();
        assertEquals(32, raw.length);

        assertEquals(fingerprint, KeyFingerprint.ofBytes(raw));
    }

    @Test
    void ofBytesRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> KeyFingerprint.ofBytes(new byte[]{1, 2, 3}));
    }

    @Test
    void toStringIsTheHexValue() throws Exception {
        KeyFingerprint fingerprint = KeyFingerprint.of(generateX25519KeyPair().getPublic());

        assertEquals(fingerprint.hex(), fingerprint.toString());
    }
}
