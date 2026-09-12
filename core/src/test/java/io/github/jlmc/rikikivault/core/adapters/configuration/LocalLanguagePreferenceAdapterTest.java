package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import io.github.jlmc.rikikivault.core.configuration.LanguagePreference;
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

/**
 * {@link LocalLanguagePreferenceAdapter} now only delegates to {@link LocalAppPreferencesAdapter} -
 * every test here injects an adapter pointed at a legacy directory that never exists, so none of
 * them can accidentally pick up this machine's real {@code ~/.rikiki-vault/git-auth/settings.json}
 * (which would otherwise be picked up as a migration source and mask the "missing" case).
 */
class LocalLanguagePreferenceAdapterTest {

    private static LocalLanguagePreferenceAdapter isolatedAdapter(Path tempDir) {
        return new LocalLanguagePreferenceAdapter(
                new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), tempDir.resolve("no-legacy-here")));
    }

    @Test
    void missingFileReturnsDefaultPreference(@TempDir Path tempDir) {
        assertEquals(LanguagePreference.defaultPreference(), isolatedAdapter(tempDir).load());
    }

    @Test
    void roundTripsTheLanguage(@TempDir Path tempDir) {
        LocalLanguagePreferenceAdapter adapter = isolatedAdapter(tempDir);
        LanguagePreference preference = new LanguagePreference(AppLanguage.EN);

        adapter.save(preference);

        assertEquals(preference, adapter.load());
    }

    @Test
    void savingTheLanguageNeverClobbersTheGitAuthSettingsStoredInTheSameFile(@TempDir Path tempDir) {
        LocalAppPreferencesAdapter preferencesAdapter =
                new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), tempDir.resolve("no-legacy-here"));
        GitAuthSettings gitAuth = new GitAuthSettings(GitAuthType.TOKEN, null, "ghp_example", null, null);
        preferencesAdapter.save(new AppPreferences(gitAuth, AppLanguage.PT, NotificationSettings.empty()));

        new LocalLanguagePreferenceAdapter(preferencesAdapter).save(new LanguagePreference(AppLanguage.EN));

        assertEquals(gitAuth, preferencesAdapter.load().gitAuth());
    }

    @Test
    void settingsFileIsOwnerOnlyOnPosixFilesystems(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported on this filesystem");
        Path dir = tempDir.resolve("preferences");
        LocalLanguagePreferenceAdapter adapter = new LocalLanguagePreferenceAdapter(
                new LocalAppPreferencesAdapter(dir, tempDir.resolve("no-legacy-here")));

        adapter.save(new LanguagePreference(AppLanguage.EN));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(dir.resolve("preferences.json"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions);
    }
}
