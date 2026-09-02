package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizeMachineServiceTest {

    private static PublicKey someKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair().getPublic();
    }

    @Test
    void addsTheRecipientAndReEncryptsEveryTrackedFile() throws Exception {
        PublicKey existingKey = someKey();
        PublicKey newKey = someKey();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(existingKey), existingKey))));
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("cv.pdf", "cv content");
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        FakeManifestPort manifestPort = new FakeManifestPort(new VaultManifest(1, List.of(
                new ManifestEntry("cv.pdf.enc", "cv.pdf", FileHash.of("cv content".getBytes(StandardCharsets.UTF_8)), "RV01"))));
        FakeEncryptionPort encryptionPort = new FakeEncryptionPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        AuthorizeMachineService service = new AuthorizeMachineService(
                recipientRegistryPort, localFiles, documentsFiles, manifestPort, encryptionPort, gitRepositoryPort);

        service.authorize(new AuthorizeMachineCommand("machine-b", newKey));

        RecipientRegistry updated = recipientRegistryPort.load();
        assertEquals(2, updated.recipients().size());
        assertTrue(updated.recipients().stream().anyMatch(r -> r.publicKey().equals(newKey)));

        assertEquals(1, encryptionPort.receivedRecipients.size());
        assertEquals(List.of(existingKey, newKey), List.copyOf(encryptionPort.receivedRecipients.get(0)));
        assertTrue(documentsFiles.listFiles().contains("cv.pdf.enc"));

        assertEquals(1, gitRepositoryPort.addedPathBatches.size());
        assertEquals(List.of("authorize machine: machine-b"), gitRepositoryPort.commitMessages);
        assertEquals(1, gitRepositoryPort.pushCallCount);
    }

    @Test
    void rejectsAFingerprintThatIsAlreadyAuthorized() throws Exception {
        PublicKey existingKey = someKey();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(existingKey), existingKey))));
        AuthorizeMachineService service = new AuthorizeMachineService(
                recipientRegistryPort, new FakeFileStoragePort(), new FakeFileStoragePort(),
                new FakeManifestPort(), new FakeEncryptionPort(), new FakeGitRepositoryPort());

        assertThrows(IllegalArgumentException.class,
                () -> service.authorize(new AuthorizeMachineCommand("machine-a-again", existingKey)));
    }
}
