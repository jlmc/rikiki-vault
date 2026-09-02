package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipientRegistryTest {

    private static Recipient someRecipient() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        PublicKey publicKey = generator.generateKeyPair().getPublic();
        return new Recipient("machine-a", KeyFingerprint.of(publicKey), publicKey);
    }

    @Test
    void emptyHasVersion1AndNoRecipients() {
        RecipientRegistry registry = RecipientRegistry.empty();

        assertEquals(1, registry.version());
        assertTrue(registry.recipients().isEmpty());
    }

    @Test
    void recipientsListIsDefensivelyCopied() throws Exception {
        List<Recipient> mutable = new ArrayList<>();
        mutable.add(someRecipient());
        RecipientRegistry registry = new RecipientRegistry(1, mutable);

        mutable.clear();

        assertEquals(1, registry.recipients().size());
    }

    @Test
    void rejectsNullRecipients() {
        assertThrows(NullPointerException.class, () -> new RecipientRegistry(1, null));
    }
}
