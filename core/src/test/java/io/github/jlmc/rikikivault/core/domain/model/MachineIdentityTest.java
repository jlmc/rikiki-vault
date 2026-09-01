package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MachineIdentityTest {

    @Test
    void toStringNeverContainsKeyMaterial() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyFingerprint id = KeyFingerprint.of(keyPair.getPublic());
        MachineIdentity identity = new MachineIdentity(id, keyPair.getPublic(), keyPair.getPrivate(), "X25519");

        String text = identity.toString();

        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        assertFalse(text.contains(privateKeyBase64), "toString must never leak the private key");
        assertFalse(text.contains(publicKeyBase64), "toString must not dump the raw public key either");
        assertEquals("MachineIdentity[id=" + id + ", keyAlgorithm=X25519]", text);
    }
}
