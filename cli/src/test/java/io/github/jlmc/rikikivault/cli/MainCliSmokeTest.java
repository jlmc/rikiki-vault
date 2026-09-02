package io.github.jlmc.rikikivault.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the {@code cli} module's composition root wires up correctly by driving {@link Main#main}
 * itself, the same way a real terminal invocation would - the only difference is
 * {@code user.home} is overridden in-process (setting the {@code HOME} env var before launching a
 * real JVM does not reliably change {@code user.home} on every platform, e.g. macOS), so the
 * machine identity used here doesn't touch the real user's {@code ~/.rikiki-vault}. A real local
 * bare remote is wired up with plain JGit first (mirroring the "clone as scaffolding" trick
 * {@code EndToEndPublishTest} uses in {@code core}, since {@code GitRepositoryPort} has no "add
 * remote" operation), so {@code publish} genuinely exercises {@code push()} instead of failing on
 * a missing remote.
 */
class MainCliSmokeTest {

    @Test
    void initStatusPublishStatusWorksEndToEndThroughTheRealCompositionRoot(
            @TempDir Path tempDir, @TempDir Path homeDir) throws Exception {
        Path bareRemote = tempDir.resolve("remote.git");
        try (Git ignored = Git.init().setDirectory(bareRemote.toFile()).setBare(true).call()) {
            // empty bare "remote" - publish below creates and pushes the first commit
        }
        Path vaultDir = tempDir.resolve("vault");
        try (Git ignored = Git.cloneRepository().setURI(bareRemote.toUri().toString()).setDirectory(vaultDir.toFile()).call()) {
            // wires "origin" for the vault directory before init/publish run through it
        }

        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", homeDir.toString());
        try {
            run("-C", vaultDir.toString(), "init", "machine-a");

            Files.createDirectories(vaultDir.resolve("local"));
            Files.writeString(vaultDir.resolve("local").resolve("cv.pdf"), "cv content", StandardCharsets.UTF_8);

            String firstStatus = run("-C", vaultDir.toString(), "status");
            assertTrue(firstStatus.contains("cv.pdf"), "status deveria listar cv.pdf como alteração, foi: " + firstStatus);

            String publishOutput = run("-C", vaultDir.toString(), "publish", "-m", "primeiro publish");
            assertTrue(publishOutput.contains("1"), "publish deveria confirmar 1 alteração publicada, foi: " + publishOutput);
            assertTrue(Files.exists(vaultDir.resolve("documents").resolve("cv.pdf.enc")));
            assertTrue(Files.exists(vaultDir.resolve("vault").resolve("manifest.json")));
            assertTrue(Files.exists(vaultDir.resolve("vault").resolve("recipients.json")));

            String secondStatus = run("-C", vaultDir.toString(), "status");
            assertTrue(secondStatus.contains("Nada para publicar"), "status deveria estar limpo depois do publish, foi: " + secondStatus);
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private static String run(String... args) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Main.main(args);
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
