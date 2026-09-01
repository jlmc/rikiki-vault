package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.ports.out.ConfigPort;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads/saves {@link VaultConfig} as YAML (Plan.md §6 names this file config.json; YAML instead
 * is a deliberate deviation made per explicit user request for this milestone). Never throws on
 * a missing, partial, or malformed file — this is local convenience configuration, not encrypted
 * vault data, so it fails safe by falling back to defaults for whatever can't be read.
 */
public final class YamlConfigFileAdapter implements ConfigPort {

    private static final String KEY_IDENTITY_DIRECTORY = "identityDirectory";
    private static final String KEY_ENCRYPTION = "encryption";
    private static final String KEY_AES_KEY_BITS = "aesKeyBits";

    private final Path configFile;

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
            Object parsed = new Yaml().load(content);
            if (!(parsed instanceof Map<?, ?> map)) {
                return VaultConfig.defaults();
            }
            return new VaultConfig(readIdentityDirectory(map), readEncryptionSettings(map));
        } catch (YAMLException e) {
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
            Files.writeString(configFile, toYaml(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write config file at " + configFile, e);
        }
    }

    private static Path readIdentityDirectory(Map<?, ?> map) {
        Object value = map.get(KEY_IDENTITY_DIRECTORY);
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Path.of(text);
            } catch (InvalidPathException ignored) {
                // fall through to the default below
            }
        }
        return VaultConfig.defaults().identityDirectory();
    }

    private static EncryptionSettings readEncryptionSettings(Map<?, ?> map) {
        if (map.get(KEY_ENCRYPTION) instanceof Map<?, ?> encryptionMap
                && encryptionMap.get(KEY_AES_KEY_BITS) instanceof Number aesKeyBits) {
            try {
                return new EncryptionSettings(aesKeyBits.intValue());
            } catch (IllegalArgumentException ignored) {
                // out-of-range value in the file; fall back to defaults rather than crash
            }
        }
        return EncryptionSettings.defaults();
    }

    private static String toYaml(VaultConfig config) {
        Map<String, Object> encryption = new LinkedHashMap<>();
        encryption.put(KEY_AES_KEY_BITS, config.encryptionSettings().aesKeyBits());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_IDENTITY_DIRECTORY, config.identityDirectory().toString());
        root.put(KEY_ENCRYPTION, encryption);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(root);
    }
}
