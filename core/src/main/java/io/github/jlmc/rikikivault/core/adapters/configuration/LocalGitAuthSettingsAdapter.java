package io.github.jlmc.rikikivault.core.adapters.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.ports.out.GitAuthSettingsPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Persists {@link GitAuthSettings} (an explicit SSH key path and/or GitHub token the user
 * configures in the app) as a small JSON file, delegating the actual write to
 * {@link LocalFileSystemAdapter} so it inherits that adapter's atomic-write and owner-only
 * ({@code rw-------}) permissions for free (Milestone 12) - the token is a secret, so it gets the
 * same treatment as the machine's private key. Fails safe to {@link GitAuthSettings#empty()} on a
 * missing or malformed file, like {@link io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter}
 * - this is local convenience configuration, not encrypted vault data.
 */
public final class LocalGitAuthSettingsAdapter implements GitAuthSettingsPort {

    private static final String SETTINGS_FILE_NAME = "settings.json";

    private final Path settingsFile;
    private final LocalFileSystemAdapter fileStorage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalGitAuthSettingsAdapter(Path gitAuthDirectory) {
        Objects.requireNonNull(gitAuthDirectory, "gitAuthDirectory must not be null");
        this.fileStorage = new LocalFileSystemAdapter(gitAuthDirectory);
        this.settingsFile = gitAuthDirectory.resolve(SETTINGS_FILE_NAME);
    }

    @Override
    public GitAuthSettings load() {
        if (!Files.exists(settingsFile)) {
            return GitAuthSettings.empty();
        }
        try {
            byte[] content = fileStorage.readFile(SETTINGS_FILE_NAME);
            SettingsDto dto = objectMapper.readValue(content, SettingsDto.class);
            if (dto == null) {
                return GitAuthSettings.empty();
            }
            Path sshKeyPath = dto.sshPrivateKeyPath() != null && !dto.sshPrivateKeyPath().isBlank()
                    ? Path.of(dto.sshPrivateKeyPath())
                    : null;
            return new GitAuthSettings(sshKeyPath, dto.githubToken());
        } catch (IOException | RuntimeException e) {
            return GitAuthSettings.empty();
        }
    }

    @Override
    public void save(GitAuthSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        try {
            SettingsDto dto = new SettingsDto(
                    settings.sshPrivateKeyPath() != null ? settings.sshPrivateKeyPath().toString() : null,
                    settings.githubToken());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dto);
            fileStorage.writeFile(SETTINGS_FILE_NAME, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record SettingsDto(String sshPrivateKeyPath, String githubToken) {
    }
}
