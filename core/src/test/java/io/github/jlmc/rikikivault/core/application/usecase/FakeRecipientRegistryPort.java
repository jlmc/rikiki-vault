package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;

final class FakeRecipientRegistryPort implements RecipientRegistryPort {

    private RecipientRegistry registry;

    FakeRecipientRegistryPort() {
        this.registry = RecipientRegistry.empty();
    }

    FakeRecipientRegistryPort(RecipientRegistry initialRegistry) {
        this.registry = initialRegistry;
    }

    @Override
    public RecipientRegistry load() {
        return registry;
    }

    @Override
    public void save(RecipientRegistry registry) {
        this.registry = registry;
    }
}
