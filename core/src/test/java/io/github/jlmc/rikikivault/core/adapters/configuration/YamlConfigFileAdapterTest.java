package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamlConfigFileAdapterTest {

    @Test
    void loadWithoutAnExistingFileReturnsDefaults(@TempDir Path tempDir) {
        YamlConfigFileAdapter adapter = new YamlConfigFileAdapter(tempDir.resolve("missing.yaml"));

        assertEquals(VaultConfig.defaults(), adapter.load());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("config").resolve("config.yaml");
        YamlConfigFileAdapter adapter = new YamlConfigFileAdapter(configFile);
        VaultConfig original = new VaultConfig(tempDir.resolve("custom-identity"), new EncryptionSettings(128));

        adapter.save(original);
        VaultConfig loaded = adapter.load();

        assertEquals(original, loaded);
    }

    @Test
    void partialYamlDefaultsMissingKeys(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, "identityDirectory: " + tempDir.resolve("only-identity") + "\n");

        VaultConfig loaded = new YamlConfigFileAdapter(configFile).load();

        assertEquals(tempDir.resolve("only-identity"), loaded.identityDirectory());
        assertEquals(EncryptionSettings.defaults(), loaded.encryptionSettings());
    }

    @Test
    void malformedYamlFallsBackToDefaultsRatherThanCrashing(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, "identityDirectory: [unterminated\n  - broken: {yaml\n");

        VaultConfig loaded = new YamlConfigFileAdapter(configFile).load();

        assertEquals(VaultConfig.defaults(), loaded);
    }

    @Test
    void outOfRangeAesKeyBitsInFileFallsBackToDefaults(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, "encryption:\n  aesKeyBits: 512\n");

        VaultConfig loaded = new YamlConfigFileAdapter(configFile).load();

        assertEquals(EncryptionSettings.defaults(), loaded.encryptionSettings());
    }

    @Test
    void nonMapYamlContentFallsBackToDefaults(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, "just a plain string\n");

        assertEquals(VaultConfig.defaults(), new YamlConfigFileAdapter(configFile).load());
    }
}
