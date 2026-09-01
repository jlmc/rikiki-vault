package io.github.jlmc.core.ports.out;

import io.github.jlmc.core.domain.model.MachineIdentity;

public interface KeyStorePort {

    void save(MachineIdentity identity);

    MachineIdentity load();

    boolean exists();
}
