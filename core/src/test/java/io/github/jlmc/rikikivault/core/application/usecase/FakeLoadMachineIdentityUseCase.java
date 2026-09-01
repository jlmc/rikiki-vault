package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;

import java.util.function.Supplier;

final class FakeLoadMachineIdentityUseCase implements LoadMachineIdentityUseCase {

    int loadCallCount = 0;
    private final Supplier<MachineIdentity> behavior;

    FakeLoadMachineIdentityUseCase(MachineIdentity identityToReturn) {
        this.behavior = () -> identityToReturn;
    }

    FakeLoadMachineIdentityUseCase(RuntimeException toThrow) {
        this.behavior = () -> {
            throw toThrow;
        };
    }

    @Override
    public MachineIdentity load() {
        loadCallCount++;
        return behavior.get();
    }
}
