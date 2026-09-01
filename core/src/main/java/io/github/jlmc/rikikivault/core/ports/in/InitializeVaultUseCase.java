package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;

public interface InitializeVaultUseCase {

    MachineIdentity initialize(InitializeVaultCommand command);
}
