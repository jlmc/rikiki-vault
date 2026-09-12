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
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads/saves {@link VaultManifest} as plain manifest.json via Jackson JSON binding — used
 * directly only by the vault-format migration (to read a pre-migration vault's plaintext
 * manifest); regular vault operations go through {@link EncryptedManifestFileAdapter}, which
 * decorates this adapter's {@link #fromJsonBytes}/{@link #toJsonBytes} to keep the manifest
 * encrypted as a whole on disk. Deliberately NOT fail-safe like
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
            byte[] content = Files.readAllBytes(manifestFile);
            return fromJsonBytes(content);
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
            Files.write(manifestFile, toJsonBytes(manifest));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write manifest file at " + manifestFile, e);
        }
    }

    /** Pure deserialization, reused by {@link EncryptedManifestFileAdapter} on decrypted bytes. */
    public VaultManifest fromJsonBytes(byte[] jsonBytes) {
        try {
            String content = new String(jsonBytes, StandardCharsets.UTF_8);
            ManifestFileDto dto = objectMapper.readValue(content, ManifestFileDto.class);
            return toDomain(dto);
        } catch (JsonProcessingException e) {
            throw new CorruptedManifestException("Manifest at " + manifestFile + " could not be parsed", e);
        }
    }

    /** Pure serialization, reused by {@link EncryptedManifestFileAdapter} before encrypting. */
    public byte[] toJsonBytes(VaultManifest manifest) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toDto(manifest));
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    private VaultManifest toDomain(ManifestFileDto dto) {
        if (dto == null || dto.files() == null || dto.hmacKeyBase64() == null) {
            throw new CorruptedManifestException("Manifest at " + manifestFile + " is missing required fields");
        }
        try {
            List<ManifestEntry> entries = dto.files().stream()
                    .map(JsonManifestFileAdapter::toDomain)
                    .collect(Collectors.toList());
            byte[] hmacKey = Base64.getDecoder().decode(dto.hmacKeyBase64());
            return new VaultManifest(dto.version(), hmacKey, entries);
        } catch (RuntimeException e) {
            throw new CorruptedManifestException("Manifest at " + manifestFile + " contains an invalid entry", e);
        }
    }

    private static ManifestEntry toDomain(ManifestEntryDto dto) {
        return new ManifestEntry(dto.id(), dto.plaintextPath(), new FileHash(dto.hash()), dto.formatVersion());
    }

    private static ManifestFileDto toDto(VaultManifest manifest) {
        List<ManifestEntryDto> entries = manifest.files().stream()
                .map(JsonManifestFileAdapter::toDto)
                .collect(Collectors.toList());
        return new ManifestFileDto(manifest.version(), Base64.getEncoder().encodeToString(manifest.hmacKey()), entries);
    }

    private static ManifestEntryDto toDto(ManifestEntry entry) {
        return new ManifestEntryDto(entry.id(), entry.plaintextPath(), entry.hash().hex(), entry.formatVersion());
    }
}
