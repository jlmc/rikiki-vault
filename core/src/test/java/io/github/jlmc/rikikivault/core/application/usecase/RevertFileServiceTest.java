package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.RevertFileCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RevertFileServiceTest {

    private static final RvEncryptedFileFormatCodec CODEC = new RvEncryptedFileFormatCodec();

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    private static EncryptedFile someEncryptedFile() {
        return new EncryptedFile("RV02", 1, 1, List.of(), new byte[12], new byte[]{1, 2, 3});
    }

    @Test
    void restoresTheLastPublishedContentOverLocalEdits() throws Exception {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "edited locally, about to be discarded");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile cvEncrypted = someEncryptedFile();
        documentsFiles.writeFile("id-cv.enc", CODEC.encode(cvEncrypted));
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(
                new ManifestEntry("id-cv", "cv.pdf", FileHash.of("published content".getBytes(StandardCharsets.UTF_8)), "RV02"))));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult(cvEncrypted, new PlaintextFile("cv.pdf", "published content".getBytes(StandardCharsets.UTF_8)));
        FakeLoadMachineIdentityUseCase loadMachineIdentityUseCase = new FakeLoadMachineIdentityUseCase(someIdentity());
        RevertFileService service = new RevertFileService(
                manifestPort, localFiles, documentsFiles, decryptFileUseCase, loadMachineIdentityUseCase);

        service.revert(new RevertFileCommand("cv.pdf"));

        assertArrayEquals("published content".getBytes(StandardCharsets.UTF_8), localFiles.readFile("cv.pdf"));
    }

    @Test
    void rejectsAPathThatWasNeverPublished() {
        FakeManifestPort manifestPort = new FakeManifestPort();
        RevertFileService service = new RevertFileService(
                manifestPort, new FakeFileStoragePort(), new FakeFileStoragePort(),
                new FakeDecryptFileUseCase(), new FakeLoadMachineIdentityUseCase(new RuntimeException("not used")));

        assertThrows(IllegalArgumentException.class, () -> service.revert(new RevertFileCommand("never-published.txt")));
    }
}
