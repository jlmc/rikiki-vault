package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.PullResult;

public interface PullVaultUseCase {

    PullResult pull();
}
