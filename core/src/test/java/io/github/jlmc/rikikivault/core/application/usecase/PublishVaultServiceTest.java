package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishVaultServiceTest {

    private static PublicKey someRecipientKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair().getPublic();
    }

    private static FakeRecipientRegistryPort registryWithOneRecipient(PublicKey publicKey) {
        return new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(publicKey), publicKey))));
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
                localFiles, documentsFiles, encryptionPort, manifestPort,
                registryWithOneRecipient(someRecipientKey()), gitRepositoryPort);

        service.publish(new PublishVaultCommand(List.of(new VaultChange(ChangeType.ADDED, "cv.pdf")), "publish cv.pdf"));

        assertEquals(1, encryptionPort.encryptCallCount);
        Optional<ManifestEntry> entry = entryFor(manifestPort.load(), "cv.pdf");
        assertTrue(entry.isPresent());
        assertTrue(documentsFiles.listFiles().contains(entry.get().documentsRelativePath()));
    }

    @Test
    void modifiedChangeReplacesTheExistingManifestEntryRatherThanDuplicatingItAndReusesItsId() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("notes.md", "new content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        ManifestEntry existing = new ManifestEntry(
                "existing-id", "notes.md", FileHash.of("old content".getBytes()), "RV02");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(existing)));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, new FakeEncryptionPort(), manifestPort,
                registryWithOneRecipient(someRecipientKey()), gitRepositoryPort);

        service.publish(new PublishVaultCommand(List.of(new VaultChange(ChangeType.MODIFIED, "notes.md")), "publish notes.md"));

        VaultManifest updated = manifestPort.load();
        assertEquals(1, updated.files().size());
        assertEquals("existing-id", updated.files().getFirst().id());
    }

    @Test
    void deletedChangeRemovesTheManifestEntryAndTheEncryptedFile() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        ManifestEntry existing = new ManifestEntry(
                "old-id", "old.pdf", FileHash.of("gone".getBytes()), "RV02");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort().withFile(existing.documentsRelativePath(), "encrypted bytes");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(existing)));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, new FakeEncryptionPort(), manifestPort,
                registryWithOneRecipient(someRecipientKey()), gitRepositoryPort);

        service.publish(new PublishVaultCommand(List.of(new VaultChange(ChangeType.DELETED, "old.pdf")), "remove old.pdf"));

        assertTrue(manifestPort.load().files().isEmpty());
        assertTrue(documentsFiles.listFiles().isEmpty());
    }

    @Test
    void aMixedBatchResultsInExactlyOneAddCommitAndPush() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort()
                .withFile("added.txt", "new file")
                .withFile("modified.txt", "new bytes");
        ManifestEntry modifiedEntry = new ManifestEntry(
                "modified-id", "modified.txt", FileHash.of("old bytes".getBytes()), "RV02");
        ManifestEntry deletedEntry = new ManifestEntry(
                "deleted-id", "deleted.txt", FileHash.of("gone".getBytes()), "RV02");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort().withFile(deletedEntry.documentsRelativePath(), "gone bytes");
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(modifiedEntry, deletedEntry)));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, new FakeEncryptionPort(), manifestPort,
                registryWithOneRecipient(someRecipientKey()), gitRepositoryPort);

        service.publish(new PublishVaultCommand(
                List.of(
                        new VaultChange(ChangeType.ADDED, "added.txt"),
                        new VaultChange(ChangeType.MODIFIED, "modified.txt"),
                        new VaultChange(ChangeType.DELETED, "deleted.txt")),
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
    void recipientsAreResolvedFromTheRegistryNotFromTheCommand() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "cv content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeEncryptionPort encryptionPort = new FakeEncryptionPort();
        PublicKey machineA = someRecipientKey();
        PublicKey machineB = someRecipientKey();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(machineA), machineA),
                new Recipient("machine-b", KeyFingerprint.of(machineB), machineB))));
        PublishVaultService service = new PublishVaultService(
                localFiles, documentsFiles, encryptionPort, new FakeManifestPort(),
                recipientRegistryPort, new FakeGitRepositoryPort());

        service.publish(new PublishVaultCommand(List.of(new VaultChange(ChangeType.ADDED, "cv.pdf")), "publish cv.pdf"));

        assertEquals(1, encryptionPort.receivedRecipients.size());
        assertEquals(List.of(machineA, machineB), List.copyOf(encryptionPort.receivedRecipients.getFirst()));
    }

    @Test
    void publishReturnsWhatGitRepositoryPortPushReturns() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "cv content");
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, new FakeFileStoragePort(), new FakeEncryptionPort(),
                new FakeManifestPort(), registryWithOneRecipient(someRecipientKey()), gitRepositoryPort);
        PublishVaultCommand command = new PublishVaultCommand(List.of(new VaultChange(ChangeType.ADDED, "cv.pdf")), "publish cv.pdf");

        assertTrue(service.publish(command), "com remoto configurado, publish deveria devolver true");

        gitRepositoryPort.pushReturnValue = false;
        assertEquals(false, service.publish(command), "sem remoto configurado, publish deveria devolver false");
    }

    @Test
    void publishLocallyNeverCallsPush() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "cv content");
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                localFiles, new FakeFileStoragePort(), new FakeEncryptionPort(),
                new FakeManifestPort(), registryWithOneRecipient(someRecipientKey()), gitRepositoryPort);

        service.publishLocally(new PublishVaultCommand(List.of(new VaultChange(ChangeType.ADDED, "cv.pdf")), "publish cv.pdf"));

        assertEquals(1, gitRepositoryPort.commitMessages.size());
        assertEquals(0, gitRepositoryPort.pushCallCount);
    }

    @Test
    void pushToRemoteDelegatesToGitRepositoryPort() {
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PublishVaultService service = new PublishVaultService(
                new FakeFileStoragePort(), new FakeFileStoragePort(), new FakeEncryptionPort(),
                new FakeManifestPort(), new FakeRecipientRegistryPort(), gitRepositoryPort);

        gitRepositoryPort.pushReturnValue = false;
        assertEquals(false, service.pushToRemote());
        assertEquals(1, gitRepositoryPort.pushCallCount);

        gitRepositoryPort.pushReturnValue = true;
        assertEquals(true, service.pushToRemote());
        assertEquals(2, gitRepositoryPort.pushCallCount);
    }
}
