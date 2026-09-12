package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import io.github.jlmc.rikikivault.core.domain.model.VaultConflict;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.ScanChangesUseCase;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullVaultServiceTest {

    private static final RvEncryptedFileFormatCodec CODEC = new RvEncryptedFileFormatCodec();
    private static final byte[] HMAC_KEY = VaultManifest.generateHmacKey();

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    private static byte marker = 0;

    private static EncryptedFile someEncryptedFile() {
        return new EncryptedFile("RV02", 1, 1, List.of(), new byte[12], new byte[]{marker++});
    }

    private static FileHash hmacOf(String content) {
        return FileHash.hmac(HMAC_KEY, content.getBytes(StandardCharsets.UTF_8));
    }

    private static PublicKey someRecipientKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair().getPublic();
    }

    /** Mirrors {@link TwoStageManifestPort}: a fixed registry on the first {@link #load()} call, another one on every later call. */
    private static final class TwoStageRecipientRegistryPort implements RecipientRegistryPort {
        private final RecipientRegistry before;
        private final RecipientRegistry after;
        private int loadCallCount = 0;

        TwoStageRecipientRegistryPort(RecipientRegistry before, RecipientRegistry after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public RecipientRegistry load() {
            loadCallCount++;
            return loadCallCount == 1 ? before : after;
        }

        @Override
        public void save(RecipientRegistry registry) {
            throw new UnsupportedOperationException("not used by PullVaultService");
        }
    }

    /** Returns a fixed manifest on the first {@link #load()} call, then another one on every later call - fakes what {@code gitRepositoryPort.pull()} does to the manifest under the hood. */
    private static final class TwoStageManifestPort implements ManifestPort {
        private final VaultManifest before;
        private final VaultManifest after;
        private int loadCallCount = 0;

        TwoStageManifestPort(VaultManifest before, VaultManifest after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public VaultManifest load() {
            loadCallCount++;
            return loadCallCount == 1 ? before : after;
        }

        @Override
        public void save(VaultManifest manifest) {
            throw new UnsupportedOperationException("not used by PullVaultService");
        }
    }

    @Test
    void remoteAdditionWithNoLocalChangeIsDecryptedAndWritten() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile cvEncrypted = someEncryptedFile();
        documentsFiles.writeFile("id-cv.enc", CODEC.encode(cvEncrypted));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult(cvEncrypted, new PlaintextFile("cv.pdf", "cv content".getBytes(StandardCharsets.UTF_8)));
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                VaultManifest.empty(),
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("cv content"), "RV02"))));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), decryptFileUseCase, () -> List.of(),
                localFiles, documentsFiles, manifestPort, gitRepositoryPort, new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        assertEquals(List.of("cv.pdf"), result.updatedPaths());
        assertTrue(result.conflicts().isEmpty());
        assertArrayEquals("cv content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("cv.pdf"));
        assertEquals(1, gitRepositoryPort.pullCallCount);
    }

    @Test
    void remoteDeletionWithNoLocalChangeIsAppliedLocally() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("old.pdf", "gone soon");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-old", "old.pdf", hmacOf("gone soon"), "RV02"))),
                VaultManifest.empty());
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), () -> List.of(),
                localFiles, documentsFiles, manifestPort, gitRepositoryPort, new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        assertEquals(List.of("old.pdf"), result.deletedPaths());
        assertTrue(result.conflicts().isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void remoteAndLocalChangeOnSamePathIsReportedAsAConflictAndLocalFileIsUntouched() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "locally edited content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        documentsFiles.writeFile("id-cv.enc", CODEC.encode(someEncryptedFile()));
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("old remote content"), "RV02"))),
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("new remote content"), "RV02"))));
        ScanChangesUseCase scanChangesUseCase = () -> List.of(new VaultChange(ChangeType.MODIFIED, "cv.pdf"));
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), scanChangesUseCase,
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort(), new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        FileHash localHash = hmacOf("locally edited content");
        FileHash remoteHash = hmacOf("new remote content");
        assertEquals(
                List.of(new VaultConflict("cv.pdf", ChangeType.MODIFIED, ChangeType.MODIFIED, localHash, remoteHash)),
                result.conflicts());
        assertTrue(result.updatedPaths().isEmpty());
        assertArrayEquals("locally edited content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("cv.pdf"));
    }

    @Test
    void unchangedEntryIsIgnored() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        ManifestEntry entry = new ManifestEntry(
                "id-cv", "cv.pdf", hmacOf("cv content"), "RV02");
        VaultManifest manifest = new VaultManifest(1, HMAC_KEY, List.of(entry));
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(manifest, manifest);
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), () -> List.of(),
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort(), new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        assertTrue(result.updatedPaths().isEmpty());
        assertTrue(result.deletedPaths().isEmpty());
        assertTrue(result.conflicts().isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void uncommittedLocalChangesAtStartReflectsTheInitialScanRegardlessOfConflicts() throws Exception {
        VaultManifest manifest = VaultManifest.empty();
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(manifest, manifest);
        List<VaultChange> localChanges = List.of(new VaultChange(ChangeType.ADDED, "draft.txt"));
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), () -> localChanges,
                new FakeFileStoragePort(), new FakeFileStoragePort(), manifestPort, new FakeGitRepositoryPort(), new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        assertEquals(localChanges, result.uncommittedLocalChangesAtStart());
        assertFalse(result.hasConflicts());
    }

    @Test
    void modifiedLocallyAndDeletedRemotelyIsReportedAsAConflictWithOnlyALocalHash() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "locally edited content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("old remote content"), "RV02"))),
                VaultManifest.empty());
        ScanChangesUseCase scanChangesUseCase = () -> List.of(new VaultChange(ChangeType.MODIFIED, "cv.pdf"));
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), scanChangesUseCase,
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort(), new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        FileHash localHash = hmacOf("locally edited content");
        assertEquals(
                List.of(new VaultConflict("cv.pdf", ChangeType.MODIFIED, ChangeType.DELETED, localHash, null)),
                result.conflicts());
        assertTrue(result.deletedPaths().isEmpty());
        assertArrayEquals("locally edited content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("cv.pdf"));
    }

    @Test
    void deletedLocallyAndModifiedRemotelyIsReportedAsAConflictWithNoLocalHash() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        documentsFiles.writeFile("id-cv.enc", CODEC.encode(someEncryptedFile()));
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("old remote content"), "RV02"))),
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("new remote content"), "RV02"))));
        ScanChangesUseCase scanChangesUseCase = () -> List.of(new VaultChange(ChangeType.DELETED, "cv.pdf"));
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), scanChangesUseCase,
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort(), new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        FileHash remoteHash = hmacOf("new remote content");
        assertEquals(
                List.of(new VaultConflict("cv.pdf", ChangeType.DELETED, ChangeType.MODIFIED, null, remoteHash)),
                result.conflicts());
        assertTrue(result.updatedPaths().isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void multipleConflictsInTheSamePullAreAllReported() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort()
                .withFile("cv.pdf", "locally edited cv")
                .withFile("notes.md", "locally edited notes");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                new VaultManifest(1, HMAC_KEY, List.of(
                        new ManifestEntry("id-cv", "cv.pdf", hmacOf("old cv"), "RV02"),
                        new ManifestEntry("id-notes", "notes.md", hmacOf("old notes"), "RV02"))),
                new VaultManifest(1, HMAC_KEY, List.of(
                        new ManifestEntry("id-cv", "cv.pdf", hmacOf("new cv"), "RV02"),
                        new ManifestEntry("id-notes", "notes.md", hmacOf("new notes"), "RV02"))));
        ScanChangesUseCase scanChangesUseCase = () -> List.of(
                new VaultChange(ChangeType.MODIFIED, "cv.pdf"), new VaultChange(ChangeType.MODIFIED, "notes.md"));
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), scanChangesUseCase,
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort(), new FakeRecipientRegistryPort());

        PullResult result = service.pull();

        assertEquals(2, result.conflicts().size());
        assertTrue(result.conflicts().stream().anyMatch(c -> c.plaintextPath().equals("cv.pdf")));
        assertTrue(result.conflicts().stream().anyMatch(c -> c.plaintextPath().equals("notes.md")));
        assertTrue(result.updatedPaths().isEmpty());
    }

    @Test
    void unchangedRecipientsResultInBothListsEmpty() throws Exception {
        PublicKey machineAKey = someRecipientKey();
        RecipientRegistry registry = new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(machineAKey), machineAKey)));
        VaultManifest manifest = VaultManifest.empty();
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), () -> List.of(),
                new FakeFileStoragePort(), new FakeFileStoragePort(), new TwoStageManifestPort(manifest, manifest),
                new FakeGitRepositoryPort(), new TwoStageRecipientRegistryPort(registry, registry));

        PullResult result = service.pull();

        assertTrue(result.newRecipients().isEmpty());
        assertTrue(result.removedRecipients().isEmpty());
    }

    @Test
    void recipientAddedAndRemovedBetweenBeforeAndAfterSnapshotsAreReportedIndependentlyOfManifestConflicts() throws Exception {
        PublicKey machineAKey = someRecipientKey();
        PublicKey machineBKey = someRecipientKey();
        Recipient machineA = new Recipient("machine-a", KeyFingerprint.of(machineAKey), machineAKey);
        Recipient machineB = new Recipient("machine-b", KeyFingerprint.of(machineBKey), machineBKey);
        RecipientRegistry before = new RecipientRegistry(1, List.of(machineA));
        RecipientRegistry after = new RecipientRegistry(2, List.of(machineB));
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "locally edited content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("old remote content"), "RV02"))),
                new VaultManifest(1, HMAC_KEY, List.of(new ManifestEntry(
                        "id-cv", "cv.pdf", hmacOf("new remote content"), "RV02"))));
        ScanChangesUseCase scanChangesUseCase = () -> List.of(new VaultChange(ChangeType.MODIFIED, "cv.pdf"));
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), scanChangesUseCase,
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort(),
                new TwoStageRecipientRegistryPort(before, after));

        PullResult result = service.pull();

        assertEquals(1, result.conflicts().size());
        assertEquals(List.of(machineB), result.newRecipients());
        assertEquals(List.of(machineA), result.removedRecipients());
    }
}
