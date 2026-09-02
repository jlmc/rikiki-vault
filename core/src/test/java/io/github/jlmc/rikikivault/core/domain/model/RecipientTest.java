package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipientTest {

    private static PublicKey somePublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return keyPair.getPublic();
    }

    @Test
    void rejectsBlankLabel() throws Exception {
        PublicKey publicKey = somePublicKey();
        assertThrows(IllegalArgumentException.class,
                () -> new Recipient(" ", KeyFingerprint.of(publicKey), publicKey));
    }

    @Test
    void rejectsNullFingerprint() throws Exception {
        PublicKey publicKey = somePublicKey();
        assertThrows(NullPointerException.class, () -> new Recipient("machine-a", null, publicKey));
    }

    @Test
    void rejectsNullPublicKey() throws Exception {
        PublicKey publicKey = somePublicKey();
        assertThrows(NullPointerException.class,
                () -> new Recipient("machine-a", KeyFingerprint.of(publicKey), null));
    }
}
