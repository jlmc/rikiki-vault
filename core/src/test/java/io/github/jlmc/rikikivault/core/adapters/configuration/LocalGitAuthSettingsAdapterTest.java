package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalGitAuthSettingsAdapterTest {

    @Test
    void missingFileReturnsEmptySettings(@TempDir Path tempDir) {
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(tempDir.resolve("git-auth"));

        assertEquals(GitAuthSettings.empty(), adapter.load());
    }

    @Test
    void malformedFileFallsBackToEmptySettings(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("git-auth");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json"), "not json", StandardCharsets.UTF_8);
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(dir);

        assertEquals(GitAuthSettings.empty(), adapter.load());
    }

    @Test
    void roundTripsAllFields(@TempDir Path tempDir) {
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(tempDir.resolve("git-auth"));
        GitAuthSettings settings = new GitAuthSettings(
                GitAuthType.SSH, Path.of("/Users/jlmc/.ssh/id_jc"), "ghp_example", "jlmc", "hunter2");

        adapter.save(settings);

        assertEquals(settings, adapter.load());
    }

    @Test
    void roundTripsWithOnlyOneFieldSet(@TempDir Path tempDir) {
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(tempDir.resolve("git-auth"));
        GitAuthSettings settings = new GitAuthSettings(GitAuthType.TOKEN, null, "ghp_example", null, null);

        adapter.save(settings);
        GitAuthSettings loaded = adapter.load();

        assertEquals(GitAuthType.TOKEN, loaded.activeType());
        assertNull(loaded.sshPrivateKeyPath());
        assertEquals("ghp_example", loaded.githubToken());
    }

    @Test
    void aFileWrittenBeforeActiveTypeExistedInfersSshWhenAKeyIsPresent(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("git-auth");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json"),
                "{\"sshPrivateKeyPath\":\"/Users/jlmc/.ssh/id_jc\",\"githubToken\":\"ghp_example\"}", StandardCharsets.UTF_8);
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(dir);

        GitAuthSettings loaded = adapter.load();

        assertEquals(GitAuthType.SSH, loaded.activeType());
        assertEquals(Path.of("/Users/jlmc/.ssh/id_jc"), loaded.sshPrivateKeyPath());
        assertEquals("ghp_example", loaded.githubToken(), "o token antigo não se perde, só deixa de estar ativo");
    }

    @Test
    void aFileWrittenBeforeActiveTypeExistedInfersTokenWhenOnlyATokenIsPresent(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("git-auth");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json"), "{\"githubToken\":\"ghp_example\"}", StandardCharsets.UTF_8);
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(dir);

        assertEquals(GitAuthType.TOKEN, adapter.load().activeType());
    }

    @Test
    void aFileWrittenBeforeActiveTypeExistedInfersNoneWhenNeitherIsPresent(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("git-auth");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json"), "{}", StandardCharsets.UTF_8);
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(dir);

        assertEquals(GitAuthType.NONE, adapter.load().activeType());
    }

    @Test
    void settingsFileIsOwnerOnlyOnPosixFilesystems(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported on this filesystem");
        Path dir = tempDir.resolve("git-auth");
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(dir);

        adapter.save(new GitAuthSettings(GitAuthType.TOKEN, null, "ghp_example", null, null));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(dir.resolve("settings.json"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions);
    }
}
