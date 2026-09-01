package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;

public interface KeyStorePort {

    void save(MachineIdentity identity);

    MachineIdentity load();

    boolean exists();
}
