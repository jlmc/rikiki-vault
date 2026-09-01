package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanChangesServiceTest {

    private final Sha256HashAdapter hashPort = new Sha256HashAdapter();

    private FileHash hashOf(String content) {
        return hashPort.hash(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fileWithNoManifestEntryIsReportedAsAdded() {
        FakeFileStoragePort storage = new FakeFileStoragePort().withFile("cv/CV.pdf", "cv content");
        ScanChangesService service = new ScanChangesService(storage, hashPort, new FakeManifestPort());

        List<VaultChange> changes = service.scan();

        assertEquals(List.of(new VaultChange(ChangeType.ADDED, "cv/CV.pdf")), changes);
    }

    @Test
    void fileWithDifferentHashThanManifestIsReportedAsModified() {
        FakeFileStoragePort storage = new FakeFileStoragePort().withFile("notes.md", "new content");
        ManifestEntry entry = new ManifestEntry("notes.md.enc", "notes.md", hashOf("old content"), "RV01");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(entry)));

        List<VaultChange> changes = new ScanChangesService(storage, hashPort, manifestPort).scan();

        assertEquals(List.of(new VaultChange(ChangeType.MODIFIED, "notes.md")), changes);
    }

    @Test
    void manifestEntryWithNoLocalFileIsReportedAsDeleted() {
        FakeFileStoragePort storage = new FakeFileStoragePort();
        ManifestEntry entry = new ManifestEntry("old.pdf.enc", "old.pdf", hashOf("gone"), "RV01");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(entry)));

        List<VaultChange> changes = new ScanChangesService(storage, hashPort, manifestPort).scan();

        assertEquals(List.of(new VaultChange(ChangeType.DELETED, "old.pdf")), changes);
    }

    @Test
    void fileWithMatchingHashIsNotReported() {
        FakeFileStoragePort storage = new FakeFileStoragePort().withFile("notes.md", "same content");
        ManifestEntry entry = new ManifestEntry("notes.md.enc", "notes.md", hashOf("same content"), "RV01");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(entry)));

        List<VaultChange> changes = new ScanChangesService(storage, hashPort, manifestPort).scan();

        assertTrue(changes.isEmpty());
    }

    @Test
    void combinedScenarioReportsEachChangeExactlyOnce() {
        FakeFileStoragePort storage = new FakeFileStoragePort()
                .withFile("added.txt", "new file")
                .withFile("modified.txt", "new bytes")
                .withFile("unchanged.txt", "same bytes");

        ManifestEntry modifiedEntry = new ManifestEntry("modified.txt.enc", "modified.txt", hashOf("old bytes"), "RV01");
        ManifestEntry unchangedEntry = new ManifestEntry("unchanged.txt.enc", "unchanged.txt", hashOf("same bytes"), "RV01");
        ManifestEntry deletedEntry = new ManifestEntry("deleted.txt.enc", "deleted.txt", hashOf("gone"), "RV01");
        FakeManifestPort manifestPort = new FakeManifestPort(
                new VaultManifest(1, List.of(modifiedEntry, unchangedEntry, deletedEntry)));

        List<VaultChange> changes = new ScanChangesService(storage, hashPort, manifestPort).scan();

        assertEquals(3, changes.size());
        assertTrue(changes.contains(new VaultChange(ChangeType.ADDED, "added.txt")));
        assertTrue(changes.contains(new VaultChange(ChangeType.MODIFIED, "modified.txt")));
        assertTrue(changes.contains(new VaultChange(ChangeType.DELETED, "deleted.txt")));
    }
}
