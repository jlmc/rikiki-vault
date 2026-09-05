package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.ScanChangesUseCase;

import java.util.List;

final class FakeScanChangesUseCase implements ScanChangesUseCase {

    private List<VaultChange> changesToReturn = List.of();

    FakeScanChangesUseCase withChanges(VaultChange... changes) {
        this.changesToReturn = List.of(changes);
        return this;
    }

    @Override
    public List<VaultChange> scan() {
        return changesToReturn;
    }
}
