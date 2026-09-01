package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

final class FakeManifestPort implements ManifestPort {

    private VaultManifest manifest;

    FakeManifestPort() {
        this.manifest = VaultManifest.empty();
    }

    FakeManifestPort(VaultManifest initialManifest) {
        this.manifest = initialManifest;
    }

    @Override
    public VaultManifest load() {
        return manifest;
    }

    @Override
    public void save(VaultManifest manifest) {
        this.manifest = manifest;
    }
}
