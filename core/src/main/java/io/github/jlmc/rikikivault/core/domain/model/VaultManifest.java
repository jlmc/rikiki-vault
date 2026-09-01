package io.github.jlmc.rikikivault.core.domain.model;

import java.util.List;
import java.util.Objects;

public record VaultManifest(int version, List<ManifestEntry> files) {

    public VaultManifest {
        Objects.requireNonNull(files, "files must not be null");
        files = List.copyOf(files);
    }

    public static VaultManifest empty() {
        return new VaultManifest(1, List.of());
    }
}
