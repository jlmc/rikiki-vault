package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.exception.UninitializedVaultException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
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

    private static EncryptedFile someEncryptedFile(byte[] marker) {
        return new EncryptedFile("RV02", 1, 1, List.of(), new byte[12], marker);
    }

    private static FakeRecipientRegistryPort registryWithOneRecipient(MachineIdentity identity) {
        return new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", identity.id(), identity.publicKey()))));
    }

    @Test
    void decryptsEveryManifestEntryIntoLocalUsingTheExistingIdentity() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile cvEncrypted = someEncryptedFile(new byte[]{1, 2, 3});
        EncryptedFile notesEncrypted = someEncryptedFile(new byte[]{4, 5, 6});
        documentsFiles.writeFile("id-cv.enc", CODEC.encode(cvEncrypted));
        documentsFiles.writeFile("id-notes.enc", CODEC.encode(notesEncrypted));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult(cvEncrypted, new PlaintextFile("cv.pdf", "cv content".getBytes(StandardCharsets.UTF_8)))
                .withResult(notesEncrypted, new PlaintextFile("notes.md", "notes content".getBytes(StandardCharsets.UTF_8)));
        VaultManifest manifest = new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(
                new ManifestEntry("id-cv", "cv.pdf", FileHash.of("cv content".getBytes(StandardCharsets.UTF_8)), "RV02"),
                new ManifestEntry("id-notes", "notes.md", FileHash.of("notes content".getBytes(StandardCharsets.UTF_8)), "RV02")));
        FakeManifestPort manifestPort = new FakeManifestPort(manifest);
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        FakeLoadMachineIdentityUseCase loadIdentityUseCase = new FakeLoadMachineIdentityUseCase(identity);
        FakeInitializeMachineIdentityUseCase initializeIdentityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        CloneVaultService service = new CloneVaultService(
                loadIdentityUseCase, initializeIdentityUseCase, decryptFileUseCase,
                localFiles, documentsFiles, manifestPort, registryWithOneRecipient(identity), gitRepositoryPort);

        MachineIdentity result = service.clone(new CloneVaultCommand("file:///some/remote.git"));

        assertEquals(identity, result);
        assertEquals(0, initializeIdentityUseCase.initializeCallCount);
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
        MachineIdentity identity = someIdentity();
        FakeLoadMachineIdentityUseCase loadIdentityUseCase = new FakeLoadMachineIdentityUseCase(identity);
        FakeInitializeMachineIdentityUseCase initializeIdentityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        CloneVaultService service = new CloneVaultService(
                loadIdentityUseCase, initializeIdentityUseCase, decryptFileUseCase,
                localFiles, documentsFiles, manifestPort, registryWithOneRecipient(identity), gitRepositoryPort);

        service.clone(new CloneVaultCommand("file:///some/remote.git"));

        assertEquals("file:///some/remote.git", gitRepositoryPort.clonedRemoteUri);
        assertTrue(decryptFileUseCase.receivedCommands.isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void whenNoIdentityExistsYetOneIsGeneratedAndThenUsedToClone() throws Exception {
        MachineIdentity generatedIdentity = someIdentity();
        FakeLoadMachineIdentityUseCase loadIdentityUseCase = new FakeLoadMachineIdentityUseCase(
                new PrivateKeyNotFoundException("no identity stored yet"));
        FakeInitializeMachineIdentityUseCase initializeIdentityUseCase = new FakeInitializeMachineIdentityUseCase(generatedIdentity);
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        CloneVaultService service = new CloneVaultService(
                loadIdentityUseCase, initializeIdentityUseCase, decryptFileUseCase,
                localFiles, documentsFiles, manifestPort, registryWithOneRecipient(someIdentity()), gitRepositoryPort);

        MachineIdentity result = service.clone(new CloneVaultCommand("file:///some/remote.git"));

        assertEquals(generatedIdentity, result);
        assertEquals(1, initializeIdentityUseCase.initializeCallCount);
        assertEquals("file:///some/remote.git", gitRepositoryPort.clonedRemoteUri);
    }

    @Test
    void cloningAnUninitializedRemoteWithNoRecipientsFails() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        FakeLoadMachineIdentityUseCase loadIdentityUseCase = new FakeLoadMachineIdentityUseCase(someIdentity());
        FakeInitializeMachineIdentityUseCase initializeIdentityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        CloneVaultService service = new CloneVaultService(
                loadIdentityUseCase, initializeIdentityUseCase, decryptFileUseCase,
                localFiles, documentsFiles, manifestPort, new FakeRecipientRegistryPort(), gitRepositoryPort);

        assertThrows(UninitializedVaultException.class,
                () -> service.clone(new CloneVaultCommand("file:///some/remote.git")));

        assertEquals("file:///some/remote.git", gitRepositoryPort.clonedRemoteUri, "the clone itself should still have been attempted");
        assertTrue(decryptFileUseCase.receivedCommands.isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }
}
