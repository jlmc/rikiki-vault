package io.github.jlmc.rikikivault.core.e2e;

import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.git.FakeGitAuthSettingsPort;
import io.github.jlmc.rikikivault.core.adapters.git.JGitRepositoryAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.adapters.manifest.EncryptedManifestFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.recipients.JsonRecipientRegistryFileAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.AuthorizeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.CloneVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PublishVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.RevokeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Composes {@link AuthorizeMachineService} and {@link RevokeMachineService} over two independent
 * {@code @TempDir}-rooted "machines" sharing one real local {@code file://} bare remote, the same
 * scaffolding style as {@code EndToEndClonePullTest}. Rather than routing the "can this machine
 * read the current ciphertext" check through a second full clone, it decrypts the current
 * {@code cv.pdf} ciphertext directly with {@link DecryptFileService} - a more direct way to prove
 * authorization actually gates access before it's granted and again after it's revoked. Uses the
 * real {@link EncryptedManifestFileAdapter} throughout, so this also exercises the manifest itself
 * being unreadable to an unauthorized machine.
 */
class EndToEndAuthorizeAndRevokeTest {

    private static final RvEncryptedFileFormatCodec CODEC = new RvEncryptedFileFormatCodec();

    @Test
    void authorizingGrantsAccessAndRevokingRemovesItAgain(@TempDir Path tempDir) throws Exception {
        Path bareRepoDir = tempDir.resolve("remote.git");
        try (Git ignored = Git.init().setDirectory(bareRepoDir.toFile()).setBare(true).call()) {
            // empty bare "remote" - machine A's first publish below creates and pushes the first commit
        }
        String remoteUri = "file://" + bareRepoDir;

        JceHybridEncryptionAdapter encryptionPort = new JceHybridEncryptionAdapter(EncryptionSettings.defaults());
        DecryptFileService decryptFileService = new DecryptFileService(encryptionPort);

        Machine machineA = new Machine(tempDir.resolve("machine-a-vault"), tempDir.resolve("machine-a-identity"), encryptionPort);
        Machine machineB = new Machine(tempDir.resolve("machine-b-vault"), tempDir.resolve("machine-b-identity"), encryptionPort);

        // Machine A initializes (she is the sole recipient) and publishes cv.pdf v1.
        machineA.gitRepositoryPort.clone(remoteUri);
        new InitializeVaultService(
                new LoadMachineIdentityService(machineA.keyStorePort),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), machineA.keyStorePort),
                new LocalFileSystemAdapter(machineA.vaultRoot), machineA.manifestPort, machineA.recipientRegistryPort, machineA.gitRepositoryPort)
                .initialize(new InitializeVaultCommand(false, "machine-a", null));

        Files.createDirectories(machineA.vaultRoot.resolve("local"));
        byte[] plaintextV1 = "cv content v1".getBytes(StandardCharsets.UTF_8);
        Files.write(machineA.vaultRoot.resolve("local").resolve("cv.pdf"), plaintextV1);
        publishAllChanges(machineA, encryptionPort, "publish cv.pdf v1");

        // Machine B already has an identity (clone loads, not generates) but is
        // not yet authorized - decrypting the current ciphertext with her key must fail.
        MachineIdentity identityB = new InitializeMachineIdentityService(
                new X25519KeyPairGeneratorAdapter(), machineB.keyStorePort).initialize();
        assertThrows(UnauthorizedMachineException.class,
                () -> decryptFileService.decrypt(new DecryptFileCommand(currentEncryptedCvPdf(machineA), identityB)));

        // Machine A authorizes Machine B - now Machine B can clone and decrypt cv.pdf v1.
        new AuthorizeMachineService(
                machineA.recipientRegistryPort, machineA.localFiles, machineA.documentsFiles,
                machineA.manifestPort, encryptionPort, machineA.gitRepositoryPort)
                .authorize(new AuthorizeMachineCommand("machine-b", identityB.publicKey()));

        MachineIdentity clonedIdentity = new CloneVaultService(
                new LoadMachineIdentityService(machineB.keyStorePort),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), machineB.keyStorePort),
                decryptFileService,
                machineB.localFiles, machineB.documentsFiles, machineB.manifestPort, machineB.recipientRegistryPort, machineB.gitRepositoryPort)
                .clone(new CloneVaultCommand(remoteUri));

        assertArrayEquals(plaintextV1, Files.readAllBytes(machineB.vaultRoot.resolve("local").resolve("cv.pdf")));
        assertArrayEquals(identityB.publicKey().getEncoded(), clonedIdentity.publicKey().getEncoded());

        // Machine A revokes Machine B - the re-encrypted ciphertext (and the re-encrypted manifest
        // itself) can no longer be decrypted with Machine B's key, satisfying the "do not just
        // delete the private key" requirement.
        new RevokeMachineService(
                machineA.recipientRegistryPort, machineA.localFiles, machineA.documentsFiles,
                machineA.manifestPort, encryptionPort, machineA.gitRepositoryPort)
                .revoke(new RevokeMachineCommand(KeyFingerprint.of(identityB.publicKey())));

        assertThrows(UnauthorizedMachineException.class,
                () -> decryptFileService.decrypt(new DecryptFileCommand(currentEncryptedCvPdf(machineA), identityB)));
    }

    private static EncryptedFile currentEncryptedCvPdf(Machine machine) {
        ManifestEntry cvEntry = machine.manifestPort.load().files().stream()
                .filter(entry -> entry.plaintextPath().equals("cv.pdf"))
                .findFirst().orElseThrow();
        return CODEC.decode(machine.documentsFiles.readFile(cvEntry.documentsRelativePath()));
    }

    private static void publishAllChanges(Machine machine, JceHybridEncryptionAdapter encryptionPort, String commitMessage) {
        ScanChangesService scanChangesService = new ScanChangesService(machine.localFiles, machine.manifestPort);
        List<VaultChange> changes = scanChangesService.scan();
        PublishVaultService publishVaultService = new PublishVaultService(
                machine.localFiles, machine.documentsFiles, encryptionPort, machine.manifestPort,
                machine.recipientRegistryPort, machine.gitRepositoryPort);
        publishVaultService.publish(new PublishVaultCommand(changes, commitMessage));
    }

    private static final class Machine {
        final Path vaultRoot;
        final LocalFileSystemAdapter localFiles;
        final LocalFileSystemAdapter documentsFiles;
        final ManifestPort manifestPort;
        final JsonRecipientRegistryFileAdapter recipientRegistryPort;
        final JGitRepositoryAdapter gitRepositoryPort;
        final LocalKeyStoreAdapter keyStorePort;

        Machine(Path vaultRoot, Path identityDirectory, JceHybridEncryptionAdapter encryptionPort) {
            this.vaultRoot = vaultRoot;
            this.localFiles = new LocalFileSystemAdapter(vaultRoot.resolve("local"));
            this.documentsFiles = new LocalFileSystemAdapter(vaultRoot.resolve("documents"));
            this.recipientRegistryPort = new JsonRecipientRegistryFileAdapter(vaultRoot.resolve("vault").resolve("recipients.json"));
            this.gitRepositoryPort = new JGitRepositoryAdapter(vaultRoot, new FakeGitAuthSettingsPort());
            this.keyStorePort = new LocalKeyStoreAdapter(identityDirectory);
            this.manifestPort = new EncryptedManifestFileAdapter(
                    vaultRoot.resolve("vault").resolve("manifest.json"), encryptionPort,
                    () -> keyStorePort.load().privateKey(),
                    () -> recipientRegistryPort.load().recipients().stream().map(Recipient::publicKey).toList());
        }
    }
}
