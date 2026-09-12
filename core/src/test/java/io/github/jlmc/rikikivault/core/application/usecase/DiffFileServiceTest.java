package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.DiffFileCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DiffFileServiceTest {

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
    void comparesThePublishedVersionAgainstTheGivenCurrentContent() throws Exception {
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        EncryptedFile cvEncrypted = someEncryptedFile();
        documentsFiles.writeFile("id-cv.enc", CODEC.encode(cvEncrypted));
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, VaultManifest.generateHmacKey(), List.of(
                new ManifestEntry("id-cv", "cv.pdf", FileHash.of("published".getBytes(StandardCharsets.UTF_8)), "RV02"))));
        FakeDecryptFileUseCase decryptFileUseCase = new FakeDecryptFileUseCase()
                .withResult(cvEncrypted, new PlaintextFile("cv.pdf", "published".getBytes(StandardCharsets.UTF_8)));
        FakeDiffPort diffPort = new FakeDiffPort();
        DiffFileService service = new DiffFileService(
                manifestPort, documentsFiles, decryptFileUseCase, new FakeLoadMachineIdentityUseCase(someIdentity()), diffPort);
        byte[] currentContent = "edited".getBytes(StandardCharsets.UTF_8);

        String result = service.diff(new DiffFileCommand("cv.pdf", currentContent));

        assertEquals("fake diff result", result);
        assertArrayEquals("published".getBytes(StandardCharsets.UTF_8), diffPort.receivedPrevious);
        assertArrayEquals(currentContent, diffPort.receivedCurrent);
    }

    @Test
    void aPathNeverPublishedComparesAgainstEmptyContent() throws Exception {
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeDiffPort diffPort = new FakeDiffPort();
        DiffFileService service = new DiffFileService(
                manifestPort, new FakeFileStoragePort(), new FakeDecryptFileUseCase(),
                new FakeLoadMachineIdentityUseCase(someIdentity()), diffPort);
        byte[] currentContent = "brand new file".getBytes(StandardCharsets.UTF_8);

        service.diff(new DiffFileCommand("new.txt", currentContent));

        assertArrayEquals(new byte[0], diffPort.receivedPrevious);
        assertArrayEquals(currentContent, diffPort.receivedCurrent);
    }
}
