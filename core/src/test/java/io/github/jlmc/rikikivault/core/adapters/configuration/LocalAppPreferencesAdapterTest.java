package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalAppPreferencesAdapterTest {

    private static LocalAppPreferencesAdapter adapter(Path tempDir) {
        return new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), tempDir.resolve("no-legacy-here"));
    }

    @Test
    void missingFileWithNoLegacyFileEitherReturnsEmptyPreferences(@TempDir Path tempDir) {
        assertEquals(AppPreferences.empty(), adapter(tempDir).load());
    }

    @Test
    void malformedFileFallsBackToEmptyPreferences(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("preferences");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("preferences.json"), "not json", StandardCharsets.UTF_8);

        assertEquals(AppPreferences.empty(), adapter(tempDir).load());
    }

    @Test
    void roundTripsGitAuthAndLanguageTogether(@TempDir Path tempDir) {
        LocalAppPreferencesAdapter adapter = adapter(tempDir);
        AppPreferences preferences = new AppPreferences(
                new GitAuthSettings(GitAuthType.SSH, Path.of("/Users/jlmc/.ssh/id_jc"), "ghp_example", "jlmc", "hunter2"),
                AppLanguage.EN);

        adapter.save(preferences);

        assertEquals(preferences, adapter.load());
    }

    @Test
    void aLegacyGitAuthFileIsMigratedWhenTheUnifiedFileDoesNotExistYet(@TempDir Path tempDir) throws Exception {
        Path legacyDir = tempDir.resolve("git-auth");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("settings.json"),
                "{\"activeType\":\"SSH\",\"sshPrivateKeyPath\":\"/Users/jlmc/.ssh/id_jc\",\"githubToken\":\"ghp_example\"}",
                StandardCharsets.UTF_8);
        LocalAppPreferencesAdapter adapter = new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), legacyDir);

        AppPreferences loaded = adapter.load();

        assertEquals(GitAuthType.SSH, loaded.gitAuth().activeType());
        assertEquals(Path.of("/Users/jlmc/.ssh/id_jc"), loaded.gitAuth().sshPrivateKeyPath());
        assertEquals(AppLanguage.PT, loaded.language(), "a legacy file never carried a language - defaults to Portuguese");
    }

    @Test
    void aLegacyFileFromBeforeActiveTypeExistedInfersItsTypeDuringMigration(@TempDir Path tempDir) throws Exception {
        Path legacyDir = tempDir.resolve("git-auth");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("settings.json"),
                "{\"sshPrivateKeyPath\":\"/Users/jlmc/.ssh/id_jc\",\"githubToken\":\"ghp_example\"}", StandardCharsets.UTF_8);
        LocalAppPreferencesAdapter adapter = new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), legacyDir);

        AppPreferences loaded = adapter.load();

        assertEquals(GitAuthType.SSH, loaded.gitAuth().activeType());
        assertEquals("ghp_example", loaded.gitAuth().githubToken(), "o token antigo não se perde, só deixa de estar ativo");
    }

    @Test
    void savingNeverTouchesTheLegacyFile(@TempDir Path tempDir) throws Exception {
        Path legacyDir = tempDir.resolve("git-auth");
        Files.createDirectories(legacyDir);
        Path legacyFile = legacyDir.resolve("settings.json");
        Files.writeString(legacyFile, "{\"githubToken\":\"ghp_example\"}", StandardCharsets.UTF_8);
        LocalAppPreferencesAdapter adapter = new LocalAppPreferencesAdapter(tempDir.resolve("preferences"), legacyDir);

        adapter.save(new AppPreferences(GitAuthSettings.empty(), AppLanguage.EN));

        assertEquals("{\"githubToken\":\"ghp_example\"}", Files.readString(legacyFile, StandardCharsets.UTF_8),
                "the legacy file is only ever read, never rewritten or deleted");
    }
}
