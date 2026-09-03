package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
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
    void roundTripsBothFields(@TempDir Path tempDir) {
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(tempDir.resolve("git-auth"));
        GitAuthSettings settings = new GitAuthSettings(Path.of("/Users/jlmc/.ssh/id_jc"), "ghp_example");

        adapter.save(settings);

        assertEquals(settings, adapter.load());
    }

    @Test
    void roundTripsWithOnlyOneFieldSet(@TempDir Path tempDir) {
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(tempDir.resolve("git-auth"));
        GitAuthSettings settings = new GitAuthSettings(null, "ghp_example");

        adapter.save(settings);
        GitAuthSettings loaded = adapter.load();

        assertNull(loaded.sshPrivateKeyPath());
        assertEquals("ghp_example", loaded.githubToken());
    }

    @Test
    void settingsFileIsOwnerOnlyOnPosixFilesystems(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported on this filesystem");
        Path dir = tempDir.resolve("git-auth");
        LocalGitAuthSettingsAdapter adapter = new LocalGitAuthSettingsAdapter(dir);

        adapter.save(new GitAuthSettings(null, "ghp_example"));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(dir.resolve("settings.json"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions);
    }
}
