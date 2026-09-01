package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.in.InitializeMachineIdentityUseCase;

import java.util.function.Supplier;

final class FakeInitializeMachineIdentityUseCase implements InitializeMachineIdentityUseCase {

    int initializeCallCount = 0;
    private final Supplier<MachineIdentity> behavior;

    FakeInitializeMachineIdentityUseCase(MachineIdentity identityToReturn) {
        this.behavior = () -> identityToReturn;
    }

    FakeInitializeMachineIdentityUseCase(RuntimeException toThrow) {
        this.behavior = () -> {
            throw toThrow;
        };
    }

    @Override
    public MachineIdentity initialize() {
        initializeCallCount++;
        return behavior.get();
    }
}
