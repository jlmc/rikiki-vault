package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RevokeMachineServiceTest {

    private static PublicKey someKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair().getPublic();
    }

    @Test
    void removesTheRecipientAndReEncryptsEveryTrackedFileForTheReducedSet() throws Exception {
        PublicKey remainingKey = someKey();
        PublicKey revokedKey = someKey();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(remainingKey), remainingKey),
                new Recipient("machine-b", KeyFingerprint.of(revokedKey), revokedKey))));
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "cv content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(
                new ManifestEntry("cv.pdf.enc", "cv.pdf", FileHash.of("cv content".getBytes(StandardCharsets.UTF_8)), "RV01"))));
        FakeEncryptionPort encryptionPort = new FakeEncryptionPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        RevokeMachineService service = new RevokeMachineService(
                recipientRegistryPort, localFiles, documentsFiles, manifestPort, encryptionPort, gitRepositoryPort);

        service.revoke(new RevokeMachineCommand(KeyFingerprint.of(revokedKey)));

        RecipientRegistry updated = recipientRegistryPort.load();
        assertEquals(1, updated.recipients().size());
        assertEquals("machine-a", updated.recipients().get(0).label());

        assertEquals(1, encryptionPort.receivedRecipients.size());
        assertEquals(List.of(remainingKey), List.copyOf(encryptionPort.receivedRecipients.get(0)));

        assertEquals(1, gitRepositoryPort.pushCallCount);
    }

    @Test
    void rejectsAFingerprintThatIsNotAuthorized() throws Exception {
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(someKey()), someKey()))));
        RevokeMachineService service = new RevokeMachineService(
                recipientRegistryPort, new FakeFileStoragePort(), new FakeFileStoragePort(),
                new FakeManifestPort(), new FakeEncryptionPort(), new FakeGitRepositoryPort());

        assertThrows(IllegalArgumentException.class,
                () -> service.revoke(new RevokeMachineCommand(KeyFingerprint.of(someKey()))));
    }

    @Test
    void rejectsRevokingTheLastRemainingRecipient() throws Exception {
        PublicKey soleKey = someKey();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(soleKey), soleKey))));
        RevokeMachineService service = new RevokeMachineService(
                recipientRegistryPort, new FakeFileStoragePort(), new FakeFileStoragePort(),
                new FakeManifestPort(), new FakeEncryptionPort(), new FakeGitRepositoryPort());

        assertThrows(IllegalStateException.class,
                () -> service.revoke(new RevokeMachineCommand(KeyFingerprint.of(soleKey))));
    }
}
