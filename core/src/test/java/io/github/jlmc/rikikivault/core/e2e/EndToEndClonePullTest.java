package io.github.jlmc.rikikivault.core.e2e;

import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.git.JGitRepositoryAdapter;
import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.adapters.manifest.JsonManifestFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.recipients.JsonRecipientRegistryFileAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.AuthorizeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.CloneVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PublishVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.PullVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composes {@link CloneVaultService} and {@link PullVaultService} over two independent
 * {@code @TempDir}-rooted "machines" sharing one real local {@code file://} bare remote, the way
 * {@code EndToEndPublishTest} does for {@code publish} alone. {@link JGitRepositoryAdapter#clone}
 * is used as test scaffolding for Machine A too (same reason as in {@code EndToEndPublishTest}:
 * the Git port has no "add remote" operation yet). Machine B generates its identity before
 * cloning - matching {@code CloneVaultService}'s corrected contract (Plan.md §10 step 4 loads an
 * existing key, it does not generate one) - and Machine A authorizes it via
 * {@link AuthorizeMachineService} before publishing, so it can decrypt what gets published.
 */
class EndToEndClonePullTest {

    @Test
    void cloneThenPullAcrossTwoMachinesAppliesRemoteChangesAndReportsConflicts(@TempDir Path tempDir) throws Exception {
        Path bareRepoDir = tempDir.resolve("remote.git");
        try (Git ignored = Git.init().setDirectory(bareRepoDir.toFile()).setBare(true).call()) {
            // empty bare "remote" - machine A's first publish below creates and pushes the first commit
        }
        String remoteUri = "file://" + bareRepoDir;

        JceHybridEncryptionAdapter encryptionPort = new JceHybridEncryptionAdapter(EncryptionSettings.defaults());
        Sha256HashAdapter hashPort = new Sha256HashAdapter();

        Machine machineA = new Machine(tempDir.resolve("machine-a-vault"), tempDir.resolve("machine-a-identity"));
        Machine machineB = new Machine(tempDir.resolve("machine-b-vault"), tempDir.resolve("machine-b-identity"));

        // Machine B generates its identity ahead of time, so its public key can be handed to
        // Machine A before anything is published (Plan.md §10 step 4: clone loads, not generates).
        MachineIdentity identityB = new InitializeMachineIdentityService(
                new X25519KeyPairGeneratorAdapter(), machineB.keyStorePort).initialize();

        // Machine A: init (wired to "origin" via the clone-as-scaffolding trick), then authorize
        // Machine B before publishing anything, so it can decrypt what's about to be published.
        machineA.gitRepositoryPort.clone(remoteUri);
        new InitializeVaultService(
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), machineA.keyStorePort),
                new LocalFileSystemAdapter(machineA.vaultRoot), machineA.manifestPort, machineA.recipientRegistryPort, machineA.gitRepositoryPort)
                .initialize(new InitializeVaultCommand(false, "machine-a"));
        new AuthorizeMachineService(
                machineA.recipientRegistryPort, machineA.localFiles, machineA.documentsFiles,
                machineA.manifestPort, encryptionPort, machineA.gitRepositoryPort)
                .authorize(new AuthorizeMachineCommand("machine-b", identityB.publicKey()));

        Files.createDirectories(machineA.vaultRoot.resolve("local"));
        writeLocalFile(machineA, "cv.pdf", "cv content v1");
        publishAllChanges(machineA, encryptionPort, hashPort, "publish cv.pdf v1");

        // Machine B clones and gets the decrypted file back, byte-for-byte. Its identity was
        // already generated above, so CloneVaultService's "load, don't create" path is exercised
        // here - the "create on the spot" path (a genuinely first-run machine with no key yet) is
        // covered by CloneVaultServiceTest instead.
        MachineIdentity clonedIdentity = new CloneVaultService(
                new LoadMachineIdentityService(machineB.keyStorePort),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), machineB.keyStorePort),
                new DecryptFileService(encryptionPort),
                machineB.localFiles, machineB.documentsFiles, machineB.manifestPort, machineB.gitRepositoryPort)
                .clone(new CloneVaultCommand(remoteUri));

        assertEquals(identityB, clonedIdentity);
        assertArrayEquals("cv content v1".getBytes(StandardCharsets.UTF_8), readLocalFile(machineB, "cv.pdf"));

        // Machine A publishes an update; Machine B pulls it cleanly (no local changes of its own).
        writeLocalFile(machineA, "cv.pdf", "cv content v2");
        publishAllChanges(machineA, encryptionPort, hashPort, "publish cv.pdf v2");

        PullResult happyPull = pullVaultService(machineB, encryptionPort, hashPort).pull();

        assertEquals(List.of("cv.pdf"), happyPull.updatedPaths());
        assertFalse(happyPull.hasConflicts());
        assertArrayEquals("cv content v2".getBytes(StandardCharsets.UTF_8), readLocalFile(machineB, "cv.pdf"));

        // Machine B edits its own copy without publishing, then Machine A publishes a conflicting
        // change - Machine B's next pull must report a conflict and leave its local file untouched.
        writeLocalFile(machineB, "cv.pdf", "cv content v3 - edited on B");
        writeLocalFile(machineA, "cv.pdf", "cv content v3 - edited on A");
        publishAllChanges(machineA, encryptionPort, hashPort, "publish cv.pdf v3");

        PullResult conflictedPull = pullVaultService(machineB, encryptionPort, hashPort).pull();

        assertEquals(1, conflictedPull.conflicts().size());
        assertEquals("cv.pdf", conflictedPull.conflicts().get(0).plaintextPath());
        assertTrue(conflictedPull.updatedPaths().isEmpty());
        assertArrayEquals("cv content v3 - edited on B".getBytes(StandardCharsets.UTF_8), readLocalFile(machineB, "cv.pdf"));
    }

    private static void writeLocalFile(Machine machine, String fileName, String content) throws Exception {
        Files.createDirectories(machine.vaultRoot.resolve("local"));
        Files.write(machine.vaultRoot.resolve("local").resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readLocalFile(Machine machine, String fileName) throws Exception {
        return Files.readAllBytes(machine.vaultRoot.resolve("local").resolve(fileName));
    }

    private static void publishAllChanges(
            Machine machine,
            JceHybridEncryptionAdapter encryptionPort,
            Sha256HashAdapter hashPort,
            String commitMessage) {
        ScanChangesService scanChangesService = new ScanChangesService(machine.localFiles, hashPort, machine.manifestPort);
        List<VaultChange> changes = scanChangesService.scan();
        PublishVaultService publishVaultService = new PublishVaultService(
                machine.localFiles, machine.documentsFiles, encryptionPort, hashPort, machine.manifestPort,
                machine.recipientRegistryPort, machine.gitRepositoryPort);
        publishVaultService.publish(new PublishVaultCommand(changes, commitMessage));
    }

    private static PullVaultService pullVaultService(Machine machine, JceHybridEncryptionAdapter encryptionPort, Sha256HashAdapter hashPort) {
        return new PullVaultService(
                new LoadMachineIdentityService(machine.keyStorePort), new DecryptFileService(encryptionPort),
                new ScanChangesService(machine.localFiles, hashPort, machine.manifestPort),
                machine.localFiles, machine.documentsFiles, machine.manifestPort, machine.gitRepositoryPort);
    }

    private static final class Machine {
        final Path vaultRoot;
        final LocalFileSystemAdapter localFiles;
        final LocalFileSystemAdapter documentsFiles;
        final JsonManifestFileAdapter manifestPort;
        final JsonRecipientRegistryFileAdapter recipientRegistryPort;
        final JGitRepositoryAdapter gitRepositoryPort;
        final LocalKeyStoreAdapter keyStorePort;

        Machine(Path vaultRoot, Path identityDirectory) {
            this.vaultRoot = vaultRoot;
            this.localFiles = new LocalFileSystemAdapter(vaultRoot.resolve("local"));
            this.documentsFiles = new LocalFileSystemAdapter(vaultRoot.resolve("documents"));
            this.manifestPort = new JsonManifestFileAdapter(vaultRoot.resolve("vault").resolve("manifest.json"));
            this.recipientRegistryPort = new JsonRecipientRegistryFileAdapter(vaultRoot.resolve("vault").resolve("recipients.json"));
            this.gitRepositoryPort = new JGitRepositoryAdapter(vaultRoot);
            this.keyStorePort = new LocalKeyStoreAdapter(identityDirectory);
        }
    }
}
