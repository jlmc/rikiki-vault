package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import io.github.jlmc.rikikivault.core.domain.model.VaultConflict;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.ScanChangesUseCase;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullVaultServiceTest {

    private static final RvEncryptedFileFormatCodec CODEC = new RvEncryptedFileFormatCodec();

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    private static byte[] someEncodedEncryptedFile(String fileName) {
        return CODEC.encode(new EncryptedFile("RV01", 1, 1, fileName, List.of(), new byte[12], new byte[]{1, 2, 3}));
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
        documentsFiles.writeFile("cv.pdf.enc", someEncodedEncryptedFile("cv.pdf"));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult("cv.pdf", new PlaintextFile("cv.pdf", "cv content".getBytes(StandardCharsets.UTF_8)));
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                VaultManifest.empty(),
                new VaultManifest(1, List.of(new ManifestEntry(
                        "cv.pdf.enc", "cv.pdf", FileHash.of("cv content".getBytes(StandardCharsets.UTF_8)), "RV01"))));
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), decryptFileUseCase, () -> List.of(),
                localFiles, documentsFiles, manifestPort, gitRepositoryPort);

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
                new VaultManifest(1, List.of(new ManifestEntry(
                        "old.pdf.enc", "old.pdf", FileHash.of("gone soon".getBytes(StandardCharsets.UTF_8)), "RV01"))),
                VaultManifest.empty());
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), () -> List.of(),
                localFiles, documentsFiles, manifestPort, gitRepositoryPort);

        PullResult result = service.pull();

        assertEquals(List.of("old.pdf"), result.deletedPaths());
        assertTrue(result.conflicts().isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void remoteAndLocalChangeOnSamePathIsReportedAsAConflictAndLocalFileIsUntouched() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "locally edited content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        documentsFiles.writeFile("cv.pdf.enc", someEncodedEncryptedFile("cv.pdf"));
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(
                new VaultManifest(1, List.of(new ManifestEntry(
                        "cv.pdf.enc", "cv.pdf", FileHash.of("old remote content".getBytes(StandardCharsets.UTF_8)), "RV01"))),
                new VaultManifest(1, List.of(new ManifestEntry(
                        "cv.pdf.enc", "cv.pdf", FileHash.of("new remote content".getBytes(StandardCharsets.UTF_8)), "RV01"))));
        ScanChangesUseCase scanChangesUseCase = () -> List.of(new VaultChange(ChangeType.MODIFIED, "cv.pdf"));
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), scanChangesUseCase,
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort());

        PullResult result = service.pull();

        assertEquals(List.of(new VaultConflict("cv.pdf", ChangeType.MODIFIED, ChangeType.MODIFIED)), result.conflicts());
        assertTrue(result.updatedPaths().isEmpty());
        assertArrayEquals("locally edited content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("cv.pdf"));
    }

    @Test
    void unchangedEntryIsIgnored() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        ManifestEntry entry = new ManifestEntry(
                "cv.pdf.enc", "cv.pdf", FileHash.of("cv content".getBytes(StandardCharsets.UTF_8)), "RV01");
        VaultManifest manifest = new VaultManifest(1, List.of(entry));
        TwoStageManifestPort manifestPort = new TwoStageManifestPort(manifest, manifest);
        PullVaultService service = new PullVaultService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeDecryptFileUseCase(), () -> List.of(),
                localFiles, documentsFiles, manifestPort, new FakeGitRepositoryPort());

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
                new FakeFileStoragePort(), new FakeFileStoragePort(), manifestPort, new FakeGitRepositoryPort());

        PullResult result = service.pull();

        assertEquals(localChanges, result.uncommittedLocalChangesAtStart());
        assertFalse(result.hasConflicts());
    }
}
