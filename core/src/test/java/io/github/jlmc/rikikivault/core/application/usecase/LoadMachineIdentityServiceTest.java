package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadMachineIdentityServiceTest {

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    @Test
    void delegatesToKeyStorePort() throws Exception {
        MachineIdentity identity = someIdentity();
        LoadMachineIdentityService service = new LoadMachineIdentityService(new FakeKeyStorePort(identity));

        assertEquals(identity, service.load());
    }

    @Test
    void propagatesPrivateKeyNotFoundException() {
        LoadMachineIdentityService service = new LoadMachineIdentityService(new FakeKeyStorePort());

        assertThrows(PrivateKeyNotFoundException.class, service::load);
    }
}
