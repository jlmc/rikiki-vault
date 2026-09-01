package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloneVaultServiceTest {

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

    @Test
    void decryptsEveryManifestEntryIntoLocal() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        documentsFiles.writeFile("cv.pdf.enc", someEncodedEncryptedFile("cv.pdf"));
        documentsFiles.writeFile("notes.md.enc", someEncodedEncryptedFile("notes.md"));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult("cv.pdf", new PlaintextFile("cv.pdf", "cv content".getBytes(StandardCharsets.UTF_8)))
                .withResult("notes.md", new PlaintextFile("notes.md", "notes content".getBytes(StandardCharsets.UTF_8)));
        VaultManifest manifest = new VaultManifest(1, List.of(
                new ManifestEntry("cv.pdf.enc", "cv.pdf", FileHash.of("cv content".getBytes(StandardCharsets.UTF_8)), "RV01"),
                new ManifestEntry("notes.md.enc", "notes.md", FileHash.of("notes content".getBytes(StandardCharsets.UTF_8)), "RV01")));
        FakeManifestPort manifestPort = new FakeManifestPort(manifest);
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(identity);
        CloneVaultService service = new CloneVaultService(
                identityUseCase, decryptFileUseCase, localFiles, documentsFiles, manifestPort, gitRepositoryPort);

        MachineIdentity result = service.clone(new CloneVaultCommand("file:///some/remote.git"));

        assertEquals(identity, result);
        assertEquals("file:///some/remote.git", gitRepositoryPort.clonedRemoteUri);
        assertArrayEquals("cv content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("cv.pdf"));
        assertArrayEquals("notes content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("notes.md"));
        assertEquals(2, decryptFileUseCase.receivedCommands.size());
    }

    @Test
    void anEmptyManifestClonesTheRepositoryWithoutDecryptingAnything() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        CloneVaultService service = new CloneVaultService(
                identityUseCase, decryptFileUseCase, localFiles, documentsFiles, manifestPort, gitRepositoryPort);

        service.clone(new CloneVaultCommand("file:///some/remote.git"));

        assertEquals("file:///some/remote.git", gitRepositoryPort.clonedRemoteUri);
        assertTrue(decryptFileUseCase.receivedCommands.isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void propagatesAndShortCircuitsWhenTheIdentityAlreadyExists() {
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(
                new MachineIdentityAlreadyExistsException("already exists"));
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        CloneVaultService service = new CloneVaultService(
                identityUseCase, decryptFileUseCase, localFiles, documentsFiles, manifestPort, gitRepositoryPort);

        assertThrows(MachineIdentityAlreadyExistsException.class,
                () -> service.clone(new CloneVaultCommand("file:///some/remote.git")));

        assertNull(gitRepositoryPort.clonedRemoteUri);
    }
}
