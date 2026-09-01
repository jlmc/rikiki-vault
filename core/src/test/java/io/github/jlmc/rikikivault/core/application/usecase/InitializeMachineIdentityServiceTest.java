package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitializeMachineIdentityServiceTest {

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    @Test
    void happyPathGeneratesAndSavesANewIdentity() {
        FakeKeyPairGeneratorPort generator = new FakeKeyPairGeneratorPort();
        FakeKeyStorePort keyStore = new FakeKeyStorePort();
        InitializeMachineIdentityService service = new InitializeMachineIdentityService(generator, keyStore);

        MachineIdentity identity = service.initialize();

        assertEquals(1, generator.generateCallCount);
        assertEquals(1, keyStore.saveCallCount);
        assertTrue(keyStore.exists());
        assertEquals(identity, keyStore.load());
    }

    @Test
    void throwsAndNeverGeneratesWhenAnIdentityAlreadyExists() throws Exception {
        FakeKeyPairGeneratorPort generator = new FakeKeyPairGeneratorPort();
        FakeKeyStorePort keyStore = new FakeKeyStorePort(someIdentity());
        InitializeMachineIdentityService service = new InitializeMachineIdentityService(generator, keyStore);

        assertThrows(MachineIdentityAlreadyExistsException.class, service::initialize);

        assertEquals(0, generator.generateCallCount);
        assertEquals(0, keyStore.saveCallCount);
    }
}
