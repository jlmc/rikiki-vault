package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.in.InitializeMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.out.KeyPairGeneratorPort;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

import java.security.KeyPair;
import java.util.Objects;

public final class InitializeMachineIdentityService implements InitializeMachineIdentityUseCase {

    private static final String KEY_ALGORITHM = "X25519";

    private final KeyPairGeneratorPort keyPairGeneratorPort;
    private final KeyStorePort keyStorePort;

    public InitializeMachineIdentityService(KeyPairGeneratorPort keyPairGeneratorPort, KeyStorePort keyStorePort) {
        this.keyPairGeneratorPort = Objects.requireNonNull(keyPairGeneratorPort, "keyPairGeneratorPort must not be null");
        this.keyStorePort = Objects.requireNonNull(keyStorePort, "keyStorePort must not be null");
    }

    @Override
    public MachineIdentity initialize() {
        if (keyStorePort.exists()) {
            throw new MachineIdentityAlreadyExistsException("A machine identity already exists; refusing to generate a new one");
        }

        KeyPair keyPair = keyPairGeneratorPort.generate();
        MachineIdentity identity = new MachineIdentity(
                KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), KEY_ALGORITHM);
        keyStorePort.save(identity);
        return identity;
    }
}
