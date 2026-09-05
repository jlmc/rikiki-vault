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

            // Reproduz o cenário reportado: apagar local/ inteiro não deve deixar os dados
            // irrecuperáveis - "restore" reconstrói-o a partir de documents/, sem precisar de rede.
            deleteRecursively(vaultDir.resolve("local"));
            assertTrue(Files.notExists(vaultDir.resolve("local").resolve("cv.pdf")));

            String restoreOutput = run("-C", vaultDir.toString(), "restore");
            assertTrue(restoreOutput.contains("cv.pdf"), "restore deveria reportar cv.pdf restaurado, foi: " + restoreOutput);
            assertTrue(Files.exists(vaultDir.resolve("local").resolve("cv.pdf")));
            assertTrue("cv content".equals(Files.readString(vaultDir.resolve("local").resolve("cv.pdf"), StandardCharsets.UTF_8)));

            // clear-local é o inverso: cv.pdf (publicado, sem alterações) é seguro para apagar;
            // um ficheiro novo nunca publicado (draft.md) fica de fora por omissão.
            Files.writeString(vaultDir.resolve("local").resolve("draft.md"), "rascunho", StandardCharsets.UTF_8);

            String clearLocalOutput = run("-C", vaultDir.toString(), "clear-local");
            assertTrue(clearLocalOutput.contains("cv.pdf"), "clear-local deveria reportar cv.pdf limpo, foi: " + clearLocalOutput);
            assertTrue(clearLocalOutput.contains("draft.md"), "clear-local deveria reportar draft.md mantido, foi: " + clearLocalOutput);
            assertTrue(Files.notExists(vaultDir.resolve("local").resolve("cv.pdf")));
            assertTrue(Files.exists(vaultDir.resolve("local").resolve("draft.md")));

            String clearLocalForcedOutput = run("-C", vaultDir.toString(), "clear-local", "--include-unpublished");
            assertTrue(clearLocalForcedOutput.contains("draft.md"), "clear-local --include-unpublished deveria reportar draft.md limpo, foi: " + clearLocalForcedOutput);
            assertTrue(Files.notExists(vaultDir.resolve("local").resolve("draft.md")));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private static void deleteRecursively(Path root) throws java.io.IOException {
        if (Files.notExists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
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
