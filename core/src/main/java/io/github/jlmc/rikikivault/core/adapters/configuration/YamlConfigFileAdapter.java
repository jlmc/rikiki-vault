package io.github.jlmc.rikikivault.core.adapters.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.ports.out.ConfigPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loads/saves {@link VaultConfig} as YAML (names this file config.yaml). Never throws on a missing, partial,
 * or malformed file — this is local convenience configuration, not encrypted vault data, so it
 * fails safe by falling back to defaults for whatever can't be read.
 */
public final class YamlConfigFileAdapter implements ConfigPort {

    private final Path configFile;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public YamlConfigFileAdapter(Path configFile) {
        this.configFile = Objects.requireNonNull(configFile, "configFile must not be null");
    }

    @Override
    public VaultConfig load() {
        if (!Files.exists(configFile)) {
            return VaultConfig.defaults();
        }
        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            ConfigFileDto dto = yamlMapper.readValue(content, ConfigFileDto.class);
            if (dto == null) {
                return VaultConfig.defaults();
            }
            return new VaultConfig(readIdentityDirectory(dto), readEncryptionSettings(dto));
        } catch (JsonProcessingException e) {
            return VaultConfig.defaults();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read config file at " + configFile, e);
        }
    }

    @Override
    public void save(VaultConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        try {
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ConfigFileDto dto = new ConfigFileDto(
                    config.identityDirectory().toString(),
                    new EncryptionSettingsDto(config.encryptionSettings().aesKeyBits()));
            Files.writeString(configFile, yamlMapper.writeValueAsString(dto), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write config file at " + configFile, e);
        }
    }

    private static Path readIdentityDirectory(ConfigFileDto dto) {
        String text = dto.identityDirectory();
        if (text != null && !text.isBlank()) {
            try {
                return Path.of(text);
            } catch (InvalidPathException ignored) {
                // fall through to the default below
            }
        }
        return VaultConfig.defaults().identityDirectory();
    }

    private static EncryptionSettings readEncryptionSettings(ConfigFileDto dto) {
        EncryptionSettingsDto encryption = dto.encryption();
        if (encryption != null && encryption.aesKeyBits() != null) {
            try {
                return new EncryptionSettings(encryption.aesKeyBits());
            } catch (IllegalArgumentException ignored) {
                // out-of-range value in the file; fall back to defaults rather than crash
            }
        }
        return EncryptionSettings.defaults();
    }
}
