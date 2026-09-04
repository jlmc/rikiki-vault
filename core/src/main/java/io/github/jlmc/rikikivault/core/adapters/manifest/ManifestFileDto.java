package io.github.jlmc.rikikivault.core.adapters.manifest;

import java.util.List;

/**
 * On-disk shape of manifest.json. Adapter-private (Jackson-bound) — kept separate
 * from {@link io.github.jlmc.rikikivault.core.domain.model.VaultManifest} so the domain model
 * stays free of any serialization concern.
 */
record ManifestFileDto(int version, List<ManifestEntryDto> files) {
}
