package io.github.jlmc.rikikivault.core.adapters.manifest;

import java.util.List;

/**
 * On-disk (well, in-JSON - see {@link EncryptedManifestFileAdapter} for what actually reaches disk)
 * shape of the manifest. Adapter-private (Jackson-bound) — kept separate from
 * {@link io.github.jlmc.rikikivault.core.domain.model.VaultManifest} so the domain model stays
 * free of any serialization concern. {@code hmacKeyBase64} carries
 * {@link io.github.jlmc.rikikivault.core.domain.model.VaultManifest#hmacKey()}.
 */
record ManifestFileDto(int version, String hmacKeyBase64, List<ManifestEntryDto> files) {
}
