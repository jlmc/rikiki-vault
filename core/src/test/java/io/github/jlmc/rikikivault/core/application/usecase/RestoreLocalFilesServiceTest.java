package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.RestoreLocalFilesResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.RestoreLocalFilesCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreLocalFilesServiceTest {

    private static final RvEncryptedFileFormatCodec CODEC = new RvEncryptedFileFormatCodec();
    private static byte marker = 0;

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    /** Each call returns a distinct value (distinct sealedContent byte) so it survives a real
     * encode/decode round trip and can be told apart as a FakeDecryptFileUseCase map key. */
    private static EncryptedFile someEncryptedFile() {
        return new EncryptedFile("RV02", 1, 1, List.of(), new byte[12], new byte[]{marker++});
    }

    @Test
    void restoresEveryMissingManifestEntry() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile cvEncrypted = someEncryptedFile();
        EncryptedFile notesEncrypted = someEncryptedFile();
        documentsFiles.writeFile("id-cv.enc", CODEC.encode(cvEncrypted));
        documentsFiles.writeFile("id-notes.enc", CODEC.encode(notesEncrypted));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult(cvEncrypted, new PlaintextFile("cv.pdf", "cv content".getBytes(StandardCharsets.UTF_8)))
                .withResult(notesEncrypted, new PlaintextFile("notes.md", "notes content".getBytes(StandardCharsets.UTF_8)));
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(
                new ManifestEntry("id-cv", "cv.pdf", FileHash.of("cv content".getBytes(StandardCharsets.UTF_8)), "RV02"),
                new ManifestEntry("id-notes", "notes.md", FileHash.of("notes content".getBytes(StandardCharsets.UTF_8)), "RV02"))));
        RestoreLocalFilesService service = new RestoreLocalFilesService(
                new FakeLoadMachineIdentityUseCase(identity), decryptFileUseCase, localFiles, documentsFiles, manifestPort);

        RestoreLocalFilesResult result = service.restore(new RestoreLocalFilesCommand(false));

        assertEquals(List.of("cv.pdf", "notes.md"), result.restoredPaths());
        assertTrue(result.skippedPaths().isEmpty());
        assertTrue(result.unauthorizedPaths().isEmpty());
        assertArrayEquals("cv content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("cv.pdf"));
        assertArrayEquals("notes content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("notes.md"));
    }

    @Test
    void withoutForceAnAlreadyExistingLocalFileIsSkippedNotOverwritten() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("notes.md", "unpublished edit");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile notesEncrypted = someEncryptedFile();
        documentsFiles.writeFile("id-notes.enc", CODEC.encode(notesEncrypted));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult(notesEncrypted, new PlaintextFile("notes.md", "published content".getBytes(StandardCharsets.UTF_8)));
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(
                new ManifestEntry("id-notes", "notes.md", FileHash.of("published content".getBytes(StandardCharsets.UTF_8)), "RV02"))));
        RestoreLocalFilesService service = new RestoreLocalFilesService(
                new FakeLoadMachineIdentityUseCase(identity), decryptFileUseCase, localFiles, documentsFiles, manifestPort);

        RestoreLocalFilesResult result = service.restore(new RestoreLocalFilesCommand(false));

        assertTrue(result.restoredPaths().isEmpty());
        assertEquals(List.of("notes.md"), result.skippedPaths());
        assertEquals("unpublished edit", new String(localFiles.readFile("notes.md"), StandardCharsets.UTF_8));
    }

    @Test
    void forceOverwritesAnAlreadyExistingLocalFile() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("notes.md", "unpublished edit");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile notesEncrypted = someEncryptedFile();
        documentsFiles.writeFile("id-notes.enc", CODEC.encode(notesEncrypted));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult(notesEncrypted, new PlaintextFile("notes.md", "published content".getBytes(StandardCharsets.UTF_8)));
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(
                new ManifestEntry("id-notes", "notes.md", FileHash.of("published content".getBytes(StandardCharsets.UTF_8)), "RV02"))));
        RestoreLocalFilesService service = new RestoreLocalFilesService(
                new FakeLoadMachineIdentityUseCase(identity), decryptFileUseCase, localFiles, documentsFiles, manifestPort);

        RestoreLocalFilesResult result = service.restore(new RestoreLocalFilesCommand(true));

        assertEquals(List.of("notes.md"), result.restoredPaths());
        assertTrue(result.skippedPaths().isEmpty());
        assertEquals("published content", new String(localFiles.readFile("notes.md"), StandardCharsets.UTF_8));
    }

    @Test
    void anUnauthorizedEntryIsReportedWithoutStoppingTheRest() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile secretEncrypted = someEncryptedFile();
        EncryptedFile notesEncrypted = someEncryptedFile();
        documentsFiles.writeFile("id-secret.enc", CODEC.encode(secretEncrypted));
        documentsFiles.writeFile("id-notes.enc", CODEC.encode(notesEncrypted));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withFailure(secretEncrypted, new UnauthorizedMachineException("not authorized"))
                .withResult(notesEncrypted, new PlaintextFile("notes.md", "notes content".getBytes(StandardCharsets.UTF_8)));
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(
                new ManifestEntry("id-secret", "secret.txt", FileHash.of("x".getBytes(StandardCharsets.UTF_8)), "RV02"),
                new ManifestEntry("id-notes", "notes.md", FileHash.of("notes content".getBytes(StandardCharsets.UTF_8)), "RV02"))));
        RestoreLocalFilesService service = new RestoreLocalFilesService(
                new FakeLoadMachineIdentityUseCase(identity), decryptFileUseCase, localFiles, documentsFiles, manifestPort);

        RestoreLocalFilesResult result = service.restore(new RestoreLocalFilesCommand(false));

        assertEquals(List.of("notes.md"), result.restoredPaths());
        assertEquals(List.of("secret.txt"), result.unauthorizedPaths());
    }

    @Test
    void anEmptyManifestRestoresNothing() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase();
        FakeManifestPort manifestPort = new FakeManifestPort();
        RestoreLocalFilesService service = new RestoreLocalFilesService(
                new FakeLoadMachineIdentityUseCase(someIdentity()), decryptFileUseCase, localFiles, documentsFiles, manifestPort);

        RestoreLocalFilesResult result = service.restore(new RestoreLocalFilesCommand(false));

        assertTrue(result.restoredPaths().isEmpty());
        assertTrue(result.skippedPaths().isEmpty());
        assertTrue(result.unauthorizedPaths().isEmpty());
    }
}
