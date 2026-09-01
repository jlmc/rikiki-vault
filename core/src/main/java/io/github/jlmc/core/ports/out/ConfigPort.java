package io.github.jlmc.core.ports.out;

import io.github.jlmc.core.configuration.VaultConfig;

public interface ConfigPort {

    VaultConfig load();

    void save(VaultConfig config);
}
