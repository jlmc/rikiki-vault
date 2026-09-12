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
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PublishVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composes only real adapters through {@link InitializeVaultService} and
 * {@link PublishVaultService} the way a later CLI would: identity + filesystem + manifest +
 * crypto + Git, over a real {@code @TempDir} tree and a real local {@code file://} bare remote.
 * {@link JGitRepositoryAdapter#clone(String)} is used here purely as test setup, to wire up the
 * "origin" remote that {@link InitializeVaultService}'s optional {@code git init} does not
 * configure.
 */
class EndToEndPublishTest {

    @Test
    void initAndPublishRoundTripThroughRealAdaptersAndAConnectedRemote(@TempDir Path tempDir) throws Exception {
        Path bareRepoDir = tempDir.resolve("remote.git");
        try (Git ignored = Git.init().setDirectory(bareRepoDir.toFile()).setBare(true).call()) {
            // empty bare "remote" - init/publish below will create and push the first commit
        }

        Path vaultRoot = tempDir.resolve("vault");
        Path localRoot = vaultRoot.resolve("local");
        Path documentsRoot = vaultRoot.resolve("documents");
        Path manifestFile = vaultRoot.resolve("vault").resolve("manifest.json");
        Path recipientsFile = vaultRoot.resolve("vault").resolve("recipients.json");
        Path identityDirectory = tempDir.resolve("identity");

        JGitRepositoryAdapter gitRepositoryPort = new JGitRepositoryAdapter(vaultRoot, new FakeGitAuthSettingsPort());
        gitRepositoryPort.clone("file://" + bareRepoDir);

        LocalKeyStoreAdapter keyStorePort = new LocalKeyStoreAdapter(identityDirectory);
        JceHybridEncryptionAdapter encryptionPort = new JceHybridEncryptionAdapter(EncryptionSettings.defaults());
        JsonRecipientRegistryFileAdapter recipientRegistryPort = new JsonRecipientRegistryFileAdapter(recipientsFile);
        ManifestPort manifestPort = new EncryptedManifestFileAdapter(
                manifestFile, encryptionPort,
                () -> keyStorePort.load().privateKey(),
                () -> recipientRegistryPort.load().recipients().stream().map(Recipient::publicKey).toList());
        InitializeVaultService initializeVaultService = new InitializeVaultService(
                new LoadMachineIdentityService(keyStorePort),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), keyStorePort),
                new LocalFileSystemAdapter(vaultRoot),
                manifestPort,
                recipientRegistryPort,
                gitRepositoryPort);

        MachineIdentity identity = initializeVaultService.initialize(new InitializeVaultCommand(false, "machine-a", null));

        assertArrayEquals("local/\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(vaultRoot.resolve(".gitignore")));
        assertTrue(manifestPort.load().files().isEmpty());
        // manifest.json itself is opaque on disk - not human-readable JSON.
        assertTrue(Files.readAllBytes(manifestFile).length > 0);
        assertArrayEquals(RvEncryptedFileFormatCodec.FORMAT_VERSION.getBytes(StandardCharsets.US_ASCII),
                java.util.Arrays.copyOfRange(Files.readAllBytes(manifestFile), 0, 4));

        Files.createDirectories(localRoot);
        byte[] plaintextContent = "cv content".getBytes(StandardCharsets.UTF_8);
        Files.write(localRoot.resolve("cv.pdf"), plaintextContent);

        LocalFileSystemAdapter localFiles = new LocalFileSystemAdapter(localRoot);
        LocalFileSystemAdapter documentsFiles = new LocalFileSystemAdapter(documentsRoot);
        ScanChangesService scanChangesService = new ScanChangesService(localFiles, manifestPort);
        List<VaultChange> changes = scanChangesService.scan();
        assertEquals(1, changes.size());

        PublishVaultService publishVaultService = new PublishVaultService(
                localFiles, documentsFiles, encryptionPort, manifestPort, recipientRegistryPort, gitRepositoryPort);

        publishVaultService.publish(new PublishVaultCommand(changes, "publish cv.pdf"));

        // The manifest reflects it, and there's nothing left for a re-scan to report.
        VaultManifest manifest = manifestPort.load();
        assertEquals(1, manifest.files().size());
        ManifestEntry cvEntry = manifest.files().getFirst();
        assertEquals("cv.pdf", cvEntry.plaintextPath());
        assertTrue(scanChangesService.scan().isEmpty());
        assertTrue(gitRepositoryPort.status().isClean());

        // documents/ carries only the opaque id, not the real filename - and no plaintext
        // directory structure either, since this file has no path separators to begin with here.
        assertTrue(java.nio.file.Files.exists(documentsRoot.resolve(cvEntry.documentsRelativePath())));
        assertTrue(cvEntry.documentsRelativePath().startsWith(cvEntry.id()));

        // The .enc file on disk round-trips back to the original plaintext bytes.
        byte[] encodedBytes = Files.readAllBytes(documentsRoot.resolve(cvEntry.documentsRelativePath()));
        EncryptedFile encryptedFile = new RvEncryptedFileFormatCodec().decode(encodedBytes);
        PlaintextFile decrypted = encryptionPort.decrypt(encryptedFile, identity.privateKey());
        assertArrayEquals(plaintextContent, decrypted.content());

        // A fresh clone of the remote proves the commit was actually pushed.
        Path freshClone = tempDir.resolve("fresh-clone");
        new JGitRepositoryAdapter(freshClone, new FakeGitAuthSettingsPort()).clone("file://" + bareRepoDir);
        assertArrayEquals(encodedBytes, Files.readAllBytes(freshClone.resolve("documents").resolve(cvEntry.documentsRelativePath())));
    }
}
