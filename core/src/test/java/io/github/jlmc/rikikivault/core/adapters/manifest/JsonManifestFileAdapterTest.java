package io.github.jlmc.rikikivault.core.adapters.manifest;

import io.github.jlmc.rikikivault.core.domain.exception.CorruptedManifestException;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonManifestFileAdapterTest {

    private static ManifestEntry someEntry(String suffix) {
        return new ManifestEntry("cv/CV" + suffix + ".pdf.enc", "cv/CV" + suffix + ".pdf",
                FileHash.of(("content-" + suffix).getBytes()), "RV01");
    }

    @Test
    void loadWithoutAnExistingFileReturnsEmptyManifest(@TempDir Path tempDir) {
        JsonManifestFileAdapter adapter = new JsonManifestFileAdapter(tempDir.resolve("missing.json"));

        assertEquals(VaultManifest.empty(), adapter.load());
    }

    @Test
    void saveThenLoadRoundTripsMultipleEntries(@TempDir Path tempDir) {
        Path manifestFile = tempDir.resolve("vault").resolve("manifest.json");
        JsonManifestFileAdapter adapter = new JsonManifestFileAdapter(manifestFile);
        VaultManifest original = new VaultManifest(1, List.of(
                someEntry("A"),
                new ManifestEntry("relatório/notas.md.enc", "relatório/notas.md", FileHash.of("café".getBytes()), "RV01")));

        adapter.save(original);
        VaultManifest loaded = adapter.load();

        assertEquals(original, loaded);
    }

    @Test
    void malformedJsonOnAnExistingFileThrowsCorruptedManifestException(@TempDir Path tempDir) throws IOException {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, "{ \"version\": 1, \"files\": [ { \"path\": \"a\" ");

        assertThrows(CorruptedManifestException.class, () -> new JsonManifestFileAdapter(manifestFile).load());
    }

    @Test
    void validJsonWithWrongShapeThrowsCorruptedManifestException(@TempDir Path tempDir) throws IOException {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, """
                {
                  "version": 1,
                  "files": [
                    { "path": "cv/CV.pdf.enc", "plaintextPath": "cv/CV.pdf" }
                  ]
                }
                """);

        assertThrows(CorruptedManifestException.class, () -> new JsonManifestFileAdapter(manifestFile).load());
    }

    @Test
    void existingButEmptyFileThrowsCorruptedManifestException(@TempDir Path tempDir) throws IOException {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, "");

        assertThrows(CorruptedManifestException.class, () -> new JsonManifestFileAdapter(manifestFile).load());
    }
}
