package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineUseCase;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

/**
 * Revokes a machine's access to this vault: removes it from the recipient registry,
 * then re-encrypts every already-tracked file for the reduced set - this is what actually revokes
 * access (the revoked machine's wrapped-key entry no longer exists in any newly published
 * ciphertext), rather than just deleting a private key locally. Publishes the result like
 * {@link PublishVaultService} does (single add/commit/push).
 */
public final class RevokeMachineService implements RevokeMachineUseCase {

    private static final Logger log = LoggerFactory.getLogger(RevokeMachineService.class);

    private final RecipientRegistryPort recipientRegistryPort;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final ManifestPort manifestPort;
    private final EncryptionPort encryptionPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public RevokeMachineService(
            RecipientRegistryPort recipientRegistryPort,
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            ManifestPort manifestPort,
            EncryptionPort encryptionPort,
            GitRepositoryPort gitRepositoryPort) {
        this.recipientRegistryPort = Objects.requireNonNull(recipientRegistryPort, "recipientRegistryPort must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.encryptionPort = Objects.requireNonNull(encryptionPort, "encryptionPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
    }

    @Override
    public boolean revoke(RevokeMachineCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        RecipientRegistry registry = recipientRegistryPort.load();
        boolean isAuthorized = registry.recipients().stream()
                .anyMatch(recipient -> recipient.fingerprint().equals(command.fingerprint()));
        if (!isAuthorized) {
            throw new IllegalArgumentException("Machine " + command.fingerprint() + " is not an authorized recipient");
        }

        List<Recipient> remainingRecipients = registry.recipients().stream()
                .filter(recipient -> !recipient.fingerprint().equals(command.fingerprint()))
                .toList();
        if (remainingRecipients.isEmpty()) {
            throw new IllegalStateException("Cannot revoke the last remaining recipient");
        }

        RecipientRegistry updatedRegistry = new RecipientRegistry(registry.version(), remainingRecipients);
        recipientRegistryPort.save(updatedRegistry);

        reEncryptAllTrackedFiles(updatedRegistry.recipients());

        gitRepositoryPort.add(List.of("."));
        gitRepositoryPort.commit("revoke machine: " + command.fingerprint());
        log.info("Revoked machine {}", command.fingerprint());
        return gitRepositoryPort.push();
    }

    private void reEncryptAllTrackedFiles(List<Recipient> recipients) {
        List<PublicKey> publicKeys = recipients.stream().map(Recipient::publicKey).toList();
        VaultManifest manifest = manifestPort.load();
        for (ManifestEntry entry : manifest.files()) {
            byte[] content = localFiles.readFile(entry.plaintextPath());
            EncryptedFile encrypted = encryptionPort.encrypt(new PlaintextFile(entry.plaintextPath(), content), publicKeys);
            documentsFiles.writeFile(entry.documentsRelativePath(), codec.encode(encrypted));
        }
        // Re-encrypts (and rewrites) the manifest itself for the reduced recipient set too - it's
        // wrapped just like any tracked file, so a revoked machine must lose the ability to decrypt
        // it (and thus read every real path) exactly like it loses access to file content.
        manifestPort.save(manifest);
    }
}
