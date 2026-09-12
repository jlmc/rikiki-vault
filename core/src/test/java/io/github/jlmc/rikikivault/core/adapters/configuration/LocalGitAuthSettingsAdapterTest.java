package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LocalGitAuthSettingsAdapter} now only delegates to {@link LocalAppPreferencesAdapter} -
 * every test here injects an adapter pointed at a legacy directory that never exists, so none of
 * them can accidentally pick up this machine's real {@code ~/.rikiki-vault/git-auth/settings.json}.
 * The migration behavior itself is covered by {@link LocalAppPreferencesAdapterTest}.
 */
class LocalGitAuthSettingsAdapterTest {

    private static LocalGitAuthSettingsAdapter isolatedAdapter(Path tempDir) {
        return new LocalGitAuthSettingsAdapter(
                new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), tempDir.resolve("no-legacy-here")));
    }

    @Test
    void missingFileReturnsEmptySettings(@TempDir Path tempDir) {
        assertEquals(GitAuthSettings.empty(), isolatedAdapter(tempDir).load());
    }

    @Test
    void roundTripsAllFields(@TempDir Path tempDir) {
        LocalGitAuthSettingsAdapter adapter = isolatedAdapter(tempDir);
        GitAuthSettings settings = new GitAuthSettings(
                GitAuthType.SSH, Path.of("/Users/jlmc/.ssh/id_jc"), "ghp_example", "jlmc", "hunter2");

        adapter.save(settings);

        assertEquals(settings, adapter.load());
    }

    @Test
    void roundTripsWithOnlyOneFieldSet(@TempDir Path tempDir) {
        LocalGitAuthSettingsAdapter adapter = isolatedAdapter(tempDir);
        GitAuthSettings settings = new GitAuthSettings(GitAuthType.TOKEN, null, "ghp_example", null, null);

        adapter.save(settings);
        GitAuthSettings loaded = adapter.load();

        assertEquals(GitAuthType.TOKEN, loaded.activeType());
        assertNull(loaded.sshPrivateKeyPath());
        assertEquals("ghp_example", loaded.githubToken());
    }

    @Test
    void savingGitAuthNeverClobbersTheLanguagePreferenceStoredInTheSameFile(@TempDir Path tempDir) {
        LocalAppPreferencesAdapter preferencesAdapter =
                new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), tempDir.resolve("no-legacy-here"));
        preferencesAdapter.save(new AppPreferences(GitAuthSettings.empty(), AppLanguage.EN, NotificationSettings.empty()));

        new LocalGitAuthSettingsAdapter(preferencesAdapter)
                .save(new GitAuthSettings(GitAuthType.TOKEN, null, "ghp_example", null, null));

        assertEquals(AppLanguage.EN, preferencesAdapter.load().language());
    }

    @Test
    void settingsFileIsOwnerOnlyOnPosixFilesystems(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported on this filesystem");
        Path dir = tempDir.resolve("preferences");
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(
                new LocalAppPreferencesAdapter(dir, tempDir.resolve("no-legacy-here")));

        adapter.save(new GitAuthSettings(GitAuthType.TOKEN, null, "ghp_example", null, null));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(dir.resolve("preferences.json"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions);
    }
}
