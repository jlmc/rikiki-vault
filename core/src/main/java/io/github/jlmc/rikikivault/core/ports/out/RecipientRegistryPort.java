package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;

public interface RecipientRegistryPort {

    RecipientRegistry load();

    void save(RecipientRegistry registry);
}
