package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.configuration.VaultConfig;

public interface ConfigPort {

    VaultConfig load();

    void save(VaultConfig config);
}
