package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;

public interface ManifestPort {

    VaultManifest load();

    void save(VaultManifest manifest);
}
