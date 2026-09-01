package io.github.jlmc.rikikivault.core.adapters.encryption;

import org.junit.jupiter.api.Test;

import javax.crypto.KeyAgreement;
import java.security.KeyPair;
import java.security.interfaces.XECKey;
import java.security.spec.NamedParameterSpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class X25519KeyPairGeneratorAdapterTest {

    private final X25519KeyPairGeneratorAdapter generator = new X25519KeyPairGeneratorAdapter();

    @Test
    void generatesAnX25519KeyPair() {
        KeyPair keyPair = generator.generate();

        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
        assertX25519(keyPair.getPublic());
        assertX25519(keyPair.getPrivate());
    }

    private static void assertX25519(java.security.Key key) {
        XECKey xecKey = assertInstanceOf(XECKey.class, key);
        assertTrue(xecKey.getParams() instanceof NamedParameterSpec spec
                        && NamedParameterSpec.X25519.getName().equalsIgnoreCase(spec.getName()),
                "Expected an X25519-parameterized key, got params: " + xecKey.getParams());
    }

    @Test
    void twoCallsProduceDifferentKeyPairs() {
        KeyPair first = generator.generate();
        KeyPair second = generator.generate();

        assertNotEquals(first.getPublic(), second.getPublic());
        assertNotEquals(first.getPrivate(), second.getPrivate());
    }

    @Test
    void generatedKeysAreUsableInKeyAgreement() throws Exception {
        KeyPair a = generator.generate();
        KeyPair b = generator.generate();

        KeyAgreement agreementA = KeyAgreement.getInstance("X25519");
        agreementA.init(a.getPrivate());
        agreementA.doPhase(b.getPublic(), true);
        byte[] secretA = agreementA.generateSecret();

        KeyAgreement agreementB = KeyAgreement.getInstance("X25519");
        agreementB.init(b.getPrivate());
        agreementB.doPhase(a.getPublic(), true);
        byte[] secretB = agreementB.generateSecret();

        assertArrayEquals(secretA, secretB, "ECDH shared secret must match on both sides");
    }
}
