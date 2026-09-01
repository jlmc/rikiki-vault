package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishVaultServiceTest {

    private static PublicKey someRecipient() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair().getPublic();
    }

    private static Optional<ManifestEntry> entryFor(VaultManifest manifest, String plaintextPath) {
        return manifest.files().stream().filter(e -> e.plaintextPath().equals(plaintextPath)).findFirst();
    }

    @Test
    void addedChangeEncryptsWritesAndAddsAManifestEntry() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "cv content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeEncryptionPort encryptionPort = new FakeEncryptionPort();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, encryptionPort, new Sha256HashAdapter(), manifestPort, gitRepositoryPort);

        service.publish(new PublishVaultCommand(
                List.of(new VaultChange(ChangeType.ADDED, "cv.pdf")), List.of(someRecipient()), "publish cv.pdf"));

        assertEquals(1, encryptionPort.encryptCallCount);
        assertTrue(documentsFiles.listFiles().contains("cv.pdf.enc"));
        Optional<ManifestEntry> entry = entryFor(manifestPort.load(), "cv.pdf");
        assertTrue(entry.isPresent());
        assertEquals("cv.pdf.enc", entry.get().path());
    }

    @Test
    void modifiedChangeReplacesTheExistingManifestEntryRatherThanDuplicatingIt() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("notes.md", "new content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        ManifestEntry existing = new ManifestEntry(
                "notes.md.enc", "notes.md", new Sha256HashAdapter().hash("old content".getBytes()), "RV01");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(existing)));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, new FakeEncryptionPort(), new Sha256HashAdapter(), manifestPort, gitRepositoryPort);

        service.publish(new PublishVaultCommand(
                List.of(new VaultChange(ChangeType.MODIFIED, "notes.md")), List.of(someRecipient()), "publish notes.md"));

        VaultManifest updated = manifestPort.load();
        assertEquals(1, updated.files().size());
        assertEquals("notes.md.enc", updated.files().get(0).path());
    }

    @Test
    void deletedChangeRemovesTheManifestEntryAndTheEncryptedFile() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort().withFile("old.pdf.enc", "encrypted bytes");
        ManifestEntry existing = new ManifestEntry(
                "old.pdf.enc", "old.pdf", new Sha256HashAdapter().hash("gone".getBytes()), "RV01");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(existing)));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, new FakeEncryptionPort(), new Sha256HashAdapter(), manifestPort, gitRepositoryPort);

        service.publish(new PublishVaultCommand(
                List.of(new VaultChange(ChangeType.DELETED, "old.pdf")), List.of(someRecipient()), "remove old.pdf"));

        assertTrue(manifestPort.load().files().isEmpty());
        assertTrue(documentsFiles.listFiles().isEmpty());
    }

    @Test
    void aMixedBatchResultsInExactlyOneAddCommitAndPush() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort()
                .withFile("added.txt", "new file")
                .withFile("modified.txt", "new bytes");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort().withFile("deleted.txt.enc", "gone bytes");
        ManifestEntry modifiedEntry = new ManifestEntry(
                "modified.txt.enc", "modified.txt", new Sha256HashAdapter().hash("old bytes".getBytes()), "RV01");
        ManifestEntry deletedEntry = new ManifestEntry(
                "deleted.txt.enc", "deleted.txt", new Sha256HashAdapter().hash("gone".getBytes()), "RV01");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(modifiedEntry, deletedEntry)));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, new FakeEncryptionPort(), new Sha256HashAdapter(), manifestPort, gitRepositoryPort);

        service.publish(new PublishVaultCommand(
                List.of(
                        new VaultChange(ChangeType.ADDED, "added.txt"),
                        new VaultChange(ChangeType.MODIFIED, "modified.txt"),
                        new VaultChange(ChangeType.DELETED, "deleted.txt")),
                List.of(someRecipient()),
                "batch publish"));

        assertEquals(1, gitRepositoryPort.addedPathBatches.size());
        assertEquals(List.of("batch publish"), gitRepositoryPort.commitMessages);
        assertEquals(1, gitRepositoryPort.pushCallCount);

        VaultManifest updated = manifestPort.load();
        assertTrue(entryFor(updated, "added.txt").isPresent());
        assertTrue(entryFor(updated, "modified.txt").isPresent());
        assertTrue(entryFor(updated, "deleted.txt").isEmpty());
    }

    @Test
    void emptyRecipientsIsRejectedByTheCommand() {
        assertThrows(IllegalArgumentException.class, () -> new PublishVaultCommand(
                List.of(new VaultChange(ChangeType.ADDED, "x.txt")), List.of(), "message"));
    }
}
