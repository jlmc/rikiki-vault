package io.github.jlmc.rikikivault.core.adapters.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.AppPreferencesPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Persists {@link AppPreferences} as a single {@code preferences.json} file (Milestone 19),
 * delegating the actual write to {@link LocalFileSystemAdapter} for atomic-write/owner-only
 * ({@code rw-------}) permissions, same as every other local-file adapter in this package.
 * <p>
 * Before this existed, Git auth settings lived on their own in {@code git-auth/settings.json}
 * (Milestone 13/17). If the unified file doesn't exist yet, {@link #load()} reads that legacy
 * file once (never deleting or rewriting it) so an already-working setup - like an SSH key
 * someone already configured - keeps working unchanged; the legacy file only stops being
 * consulted once something is actually {@link #save(AppPreferences) saved} through this adapter.
 */
public final class LocalAppPreferencesAdapter implements AppPreferencesPort {

    private static final String SETTINGS_FILE_NAME = "preferences.json";
    private static final String LEGACY_GIT_AUTH_FILE_NAME = "settings.json";

    private final Path settingsFile;
    private final LocalFileSystemAdapter fileStorage;
    private final Path legacyGitAuthDirectory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalAppPreferencesAdapter(Path preferencesDirectory) {
        this(preferencesDirectory, VaultPaths.defaultGitAuthDirectory());
    }

    LocalAppPreferencesAdapter(Path preferencesDirectory, Path legacyGitAuthDirectory) {
        Objects.requireNonNull(preferencesDirectory, "preferencesDirectory must not be null");
        this.fileStorage = new LocalFileSystemAdapter(preferencesDirectory);
        this.settingsFile = preferencesDirectory.resolve(SETTINGS_FILE_NAME);
        this.legacyGitAuthDirectory = Objects.requireNonNull(legacyGitAuthDirectory, "legacyGitAuthDirectory must not be null");
    }

    @Override
    public AppPreferences load() {
        if (!Files.exists(settingsFile)) {
            return migrateLegacyGitAuthFile();
        }
        try {
            byte[] content = fileStorage.readFile(SETTINGS_FILE_NAME);
            SettingsDto dto = objectMapper.readValue(content, SettingsDto.class);
            return dto == null ? AppPreferences.empty() : toAppPreferences(dto);
        } catch (IOException | RuntimeException e) {
            return AppPreferences.empty();
        }
    }

    @Override
    public void save(AppPreferences preferences) {
        Objects.requireNonNull(preferences, "preferences must not be null");
        try {
            SettingsDto dto = fromAppPreferences(preferences);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dto);
            fileStorage.writeFile(SETTINGS_FILE_NAME, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AppPreferences migrateLegacyGitAuthFile() {
        Path legacyFile = legacyGitAuthDirectory.resolve(LEGACY_GIT_AUTH_FILE_NAME);
        if (!Files.exists(legacyFile)) {
            return AppPreferences.empty();
        }
        try {
            byte[] content = new LocalFileSystemAdapter(legacyGitAuthDirectory).readFile(LEGACY_GIT_AUTH_FILE_NAME);
            GitAuthDto dto = objectMapper.readValue(content, GitAuthDto.class);
            if (dto == null) {
                return AppPreferences.empty();
            }
            return new AppPreferences(toGitAuthSettings(dto), AppLanguage.PT, NotificationSettings.empty());
        } catch (IOException | RuntimeException e) {
            return AppPreferences.empty();
        }
    }

    private static AppPreferences toAppPreferences(SettingsDto dto) {
        GitAuthSettings gitAuth = dto.gitAuth() == null ? GitAuthSettings.empty() : toGitAuthSettings(dto.gitAuth());
        AppLanguage language = parseLanguageOrDefault(dto.language());
        NotificationSettings notifications = toNotificationSettings(dto.notifications());
        return new AppPreferences(gitAuth, language, notifications);
    }

    /**
     * A {@code preferences.json} written before this field existed has no {@code notifications}
     * object at all - default to {@link NotificationSettings#empty()} instead of failing to load.
     */
    private static NotificationSettings toNotificationSettings(NotificationsDto dto) {
        if (dto == null) {
            return NotificationSettings.empty();
        }
        int autoDismissSeconds = dto.autoDismissSeconds() != null ? dto.autoDismissSeconds() : NotificationSettings.empty().autoDismissSeconds();
        return new NotificationSettings(dto.autoDismiss(), autoDismissSeconds);
    }

    private static GitAuthSettings toGitAuthSettings(GitAuthDto dto) {
        Path sshKeyPath = dto.sshPrivateKeyPath() != null && !dto.sshPrivateKeyPath().isBlank()
                ? Path.of(dto.sshPrivateKeyPath())
                : null;
        GitAuthType activeType = parseActiveType(dto.activeType(), sshKeyPath, dto.githubToken());
        return new GitAuthSettings(activeType, sshKeyPath, dto.githubToken(), dto.httpUsername(), dto.httpPassword());
    }

    /**
     * A file written before Milestone 17 (only two fields) has no {@code activeType} - infer it
     * from what's configured, exactly as Milestone 17 originally did directly in this class.
     */
    private static GitAuthType parseActiveType(String rawType, Path sshKeyPath, String githubToken) {
        if (rawType != null) {
            try {
                return GitAuthType.valueOf(rawType);
            } catch (IllegalArgumentException ignored) {
                // fall through to inference below
            }
        }
        if (sshKeyPath != null) {
            return GitAuthType.SSH;
        }
        if (githubToken != null && !githubToken.isBlank()) {
            return GitAuthType.TOKEN;
        }
        return GitAuthType.NONE;
    }

    private static AppLanguage parseLanguageOrDefault(String rawLanguage) {
        if (rawLanguage == null) {
            return AppLanguage.PT;
        }
        try {
            return AppLanguage.valueOf(rawLanguage);
        } catch (IllegalArgumentException e) {
            return AppLanguage.PT;
        }
    }

    private static SettingsDto fromAppPreferences(AppPreferences preferences) {
        GitAuthSettings g = preferences.gitAuth();
        GitAuthDto gitAuthDto = new GitAuthDto(
                g.activeType().name(),
                g.sshPrivateKeyPath() != null ? g.sshPrivateKeyPath().toString() : null,
                g.githubToken(), g.httpUsername(), g.httpPassword());
        NotificationsDto notificationsDto = new NotificationsDto(
                preferences.notifications().autoDismiss(), preferences.notifications().autoDismissSeconds());
        return new SettingsDto(gitAuthDto, preferences.language().name(), notificationsDto);
    }

    private record SettingsDto(GitAuthDto gitAuth, String language, NotificationsDto notifications) {
    }

    private record GitAuthDto(
            String activeType, String sshPrivateKeyPath, String githubToken, String httpUsername, String httpPassword) {
    }

    private record NotificationsDto(boolean autoDismiss, Integer autoDismissSeconds) {
    }
}
