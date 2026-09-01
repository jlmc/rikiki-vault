package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;

import java.util.List;

public interface ScanChangesUseCase {

    List<VaultChange> scan();
}
