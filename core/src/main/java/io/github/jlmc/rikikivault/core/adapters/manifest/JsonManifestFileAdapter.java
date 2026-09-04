package io.github.jlmc.rikikivault.core.adapters.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.rikikivault.core.domain.exception.CorruptedManifestException;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads/saves {@link VaultManifest} as manifest.json via plain Jackson JSON
 * binding. Deliberately NOT fail-safe like
 * {@link io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter}: a missing
 * file legitimately means "nothing encrypted yet" ({@link VaultManifest#empty()}), but an
 * existing file that fails to parse or has the wrong shape throws
 * {@link CorruptedManifestException} rather than silently defaulting to empty — the manifest is
 * data-integrity-relevant to change detection, so corruption must be surfaced, not masked.
 */
public final class JsonManifestFileAdapter implements ManifestPort {

    private final Path manifestFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonManifestFileAdapter(Path manifestFile) {
        this.manifestFile = Objects.requireNonNull(manifestFile, "manifestFile must not be null");
    }

    @Override
    public VaultManifest load() {
        if (!Files.exists(manifestFile)) {
            return VaultManifest.empty();
        }
        try {
            String content = Files.readString(manifestFile, StandardCharsets.UTF_8);
            ManifestFileDto dto = objectMapper.readValue(content, ManifestFileDto.class);
            return toDomain(dto);
        } catch (JsonProcessingException e) {
            throw new CorruptedManifestException("Manifest at " + manifestFile + " could not be parsed", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read manifest file at " + manifestFile, e);
        }
    }

    @Override
    public void save(VaultManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        try {
            Path parent = manifestFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toDto(manifest));
            Files.writeString(manifestFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write manifest file at " + manifestFile, e);
        }
    }

    private VaultManifest toDomain(ManifestFileDto dto) {
        if (dto == null || dto.files() == null) {
            throw new CorruptedManifestException("Manifest at " + manifestFile + " is missing required fields");
        }
        try {
            List<ManifestEntry> entries = dto.files().stream()
                    .map(JsonManifestFileAdapter::toDomain)
                    .collect(Collectors.toList());
            return new VaultManifest(dto.version(), entries);
        } catch (RuntimeException e) {
            throw new CorruptedManifestException("Manifest at " + manifestFile + " contains an invalid entry", e);
        }
    }

    private static ManifestEntry toDomain(ManifestEntryDto dto) {
        return new ManifestEntry(dto.path(), dto.plaintextPath(), new FileHash(dto.hash()), dto.formatVersion());
    }

    private static ManifestFileDto toDto(VaultManifest manifest) {
        List<ManifestEntryDto> entries = manifest.files().stream()
                .map(JsonManifestFileAdapter::toDto)
                .collect(Collectors.toList());
        return new ManifestFileDto(manifest.version(), entries);
    }

    private static ManifestEntryDto toDto(ManifestEntry entry) {
        return new ManifestEntryDto(entry.path(), entry.plaintextPath(), entry.hash().hex(), entry.formatVersion());
    }
}
