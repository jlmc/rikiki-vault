package io.github.jlmc.rikikivault.core.adapters.recipients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.rikikivault.core.domain.exception.CorruptedRecipientRegistryException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads/saves {@link RecipientRegistry} as recipients.json (Plan.md §4) via plain Jackson JSON
 * binding, mirroring {@link io.github.jlmc.rikikivault.core.adapters.manifest.JsonManifestFileAdapter}.
 * A missing file means "no recipients yet" ({@link RecipientRegistry#empty()}); an existing file
 * that fails to parse throws {@link CorruptedRecipientRegistryException} rather than defaulting to
 * empty, since silently losing who is authorized would be a security-relevant data loss. Each
 * public key is stored as Base64 of its X.509-encoded bytes and reconstructed with the same
 * {@code KeyFactory}/algorithm {@code LocalKeyStoreAdapter} already uses for {@code public.key}.
 */
public final class JsonRecipientRegistryFileAdapter implements RecipientRegistryPort {

    private static final String KEY_ALGORITHM = "X25519";

    private final Path recipientsFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonRecipientRegistryFileAdapter(Path recipientsFile) {
        this.recipientsFile = Objects.requireNonNull(recipientsFile, "recipientsFile must not be null");
    }

    @Override
    public RecipientRegistry load() {
        if (!Files.exists(recipientsFile)) {
            return RecipientRegistry.empty();
        }
        try {
            String content = Files.readString(recipientsFile, StandardCharsets.UTF_8);
            RecipientRegistryDto dto = objectMapper.readValue(content, RecipientRegistryDto.class);
            return toDomain(dto);
        } catch (JsonProcessingException e) {
            throw new CorruptedRecipientRegistryException("Recipient registry at " + recipientsFile + " could not be parsed", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read recipient registry file at " + recipientsFile, e);
        }
    }

    @Override
    public void save(RecipientRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        try {
            Path parent = recipientsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toDto(registry));
            Files.writeString(recipientsFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write recipient registry file at " + recipientsFile, e);
        }
    }

    private RecipientRegistry toDomain(RecipientRegistryDto dto) {
        if (dto == null || dto.recipients() == null) {
            throw new CorruptedRecipientRegistryException("Recipient registry at " + recipientsFile + " is missing required fields");
        }
        try {
            List<Recipient> recipients = dto.recipients().stream()
                    .map(this::toDomain)
                    .collect(Collectors.toList());
            return new RecipientRegistry(dto.version(), recipients);
        } catch (RuntimeException e) {
            throw new CorruptedRecipientRegistryException("Recipient registry at " + recipientsFile + " contains an invalid entry", e);
        }
    }

    private Recipient toDomain(RecipientDto dto) {
        PublicKey publicKey = decodePublicKey(dto.publicKey());
        return new Recipient(dto.label(), new KeyFingerprint(dto.fingerprint()), publicKey);
    }

    private static RecipientRegistryDto toDto(RecipientRegistry registry) {
        List<RecipientDto> recipients = registry.recipients().stream()
                .map(JsonRecipientRegistryFileAdapter::toDto)
                .collect(Collectors.toList());
        return new RecipientRegistryDto(registry.version(), recipients);
    }

    private static RecipientDto toDto(Recipient recipient) {
        String encodedPublicKey = Base64.getEncoder().encodeToString(recipient.publicKey().getEncoded());
        return new RecipientDto(recipient.label(), recipient.fingerprint().hex(), encodedPublicKey);
    }

    private PublicKey decodePublicKey(String base64) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new CorruptedRecipientRegistryException("Recipient registry at " + recipientsFile + " contains an unreadable public key", e);
        }
    }
}
