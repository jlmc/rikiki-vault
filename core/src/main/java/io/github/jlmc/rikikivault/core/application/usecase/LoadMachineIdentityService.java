package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

import java.util.Objects;

public final class LoadMachineIdentityService implements LoadMachineIdentityUseCase {

    private final KeyStorePort keyStorePort;

    public LoadMachineIdentityService(KeyStorePort keyStorePort) {
        this.keyStorePort = Objects.requireNonNull(keyStorePort, "keyStorePort must not be null");
    }

    @Override
    public MachineIdentity load() {
        return keyStorePort.load();
    }
}
