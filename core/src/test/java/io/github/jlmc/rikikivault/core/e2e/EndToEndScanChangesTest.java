package io.github.jlmc.rikikivault.core.e2e;

import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.adapters.manifest.JsonManifestFileAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.out.HashPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composes only real adapters (no fakes) through {@link ScanChangesService} the way a later CLI
 * would: filesystem scan + hashing + manifest, over a real {@code @TempDir} tree. Not a full
 * {@code publish} E2E (that needs Git) — just this milestone's read/discovery slice.
 */
class EndToEndScanChangesTest {

    @Test
    void scanReflectsRealFilesystemChangesAcrossSuccessiveRuns(@TempDir Path tempDir) throws IOException {
        Path localRoot = tempDir.resolve("local");
        Files.createDirectories(localRoot);
        Path manifestFile = tempDir.resolve("vault").resolve("manifest.json");

        Files.writeString(localRoot.resolve("cv.pdf"), "original cv content");
        Files.writeString(localRoot.resolve("notes.md"), "original notes content");

        HashPort hashPort = new Sha256HashAdapter();
        ManifestPort manifestPort = new JsonManifestFileAdapter(manifestFile);
        ScanChangesService scanChanges = new ScanChangesService(new LocalFileSystemAdapter(localRoot), hashPort, manifestPort);

        // 1. No manifest yet -> everything is ADDED.
        List<VaultChange> firstScan = scanChanges.scan();
        assertEquals(2, firstScan.size());
        assertTrue(firstScan.contains(new VaultChange(ChangeType.ADDED, "cv.pdf")));
        assertTrue(firstScan.contains(new VaultChange(ChangeType.ADDED, "notes.md")));

        // 2. Record cv.pdf's current hash in the manifest -> it's no longer reported, notes.md still is.
        byte[] cvContent = Files.readAllBytes(localRoot.resolve("cv.pdf"));
        ManifestEntry cvEntry = new ManifestEntry("cv.pdf.enc", "cv.pdf", hashPort.hash(cvContent), "RV01");
        manifestPort.save(new VaultManifest(1, List.of(cvEntry)));

        List<VaultChange> secondScan = scanChanges.scan();
        assertEquals(List.of(new VaultChange(ChangeType.ADDED, "notes.md")), secondScan);

        // 3. Modify cv.pdf's bytes -> it flips to MODIFIED.
        Files.writeString(localRoot.resolve("cv.pdf"), "mutated cv content", StandardCharsets.UTF_8);
        List<VaultChange> thirdScan = scanChanges.scan();
        assertTrue(thirdScan.contains(new VaultChange(ChangeType.MODIFIED, "cv.pdf")));

        // 4. Delete cv.pdf from disk -> it's reported as DELETED (manifest still references it).
        Files.delete(localRoot.resolve("cv.pdf"));
        List<VaultChange> fourthScan = scanChanges.scan();
        assertTrue(fourthScan.contains(new VaultChange(ChangeType.DELETED, "cv.pdf")));
        assertTrue(fourthScan.contains(new VaultChange(ChangeType.ADDED, "notes.md")));
    }
}
