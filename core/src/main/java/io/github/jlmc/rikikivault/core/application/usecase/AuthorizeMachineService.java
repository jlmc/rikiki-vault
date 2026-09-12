package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineUseCase;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Grants a machine access to this vault: adds it to the recipient registry, then
 * re-encrypts every already-tracked file for the updated recipient set - without this, the newly
 * authorized machine could not decrypt anything published before it was authorized. Publishes the
 * result like {@link PublishVaultService} does (single add/commit/push).
 */
public final class AuthorizeMachineService implements AuthorizeMachineUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeMachineService.class);

    private final RecipientRegistryPort recipientRegistryPort;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final ManifestPort manifestPort;
    private final EncryptionPort encryptionPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public AuthorizeMachineService(
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
    public boolean authorize(AuthorizeMachineCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        RecipientRegistry registry = recipientRegistryPort.load();
        KeyFingerprint fingerprint = KeyFingerprint.of(command.publicKey());
        boolean alreadyAuthorized = registry.recipients().stream()
                .anyMatch(recipient -> recipient.fingerprint().equals(fingerprint));
        if (alreadyAuthorized) {
            throw new IllegalArgumentException("Machine " + fingerprint + " is already authorized");
        }

        List<Recipient> updatedRecipients = new ArrayList<>(registry.recipients());
        updatedRecipients.add(new Recipient(command.label(), fingerprint, command.publicKey()));
        RecipientRegistry updatedRegistry = new RecipientRegistry(registry.version(), updatedRecipients);
        recipientRegistryPort.save(updatedRegistry);

        reEncryptAllTrackedFiles(updatedRegistry.recipients());

        gitRepositoryPort.add(List.of("."));
        gitRepositoryPort.commit("authorize machine: " + command.label());
        log.info("Authorized machine '{}' ({})", command.label(), fingerprint);
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
        // Re-encrypts (and rewrites) the manifest itself for the updated recipient set too - the
        // manifest is wrapped just like any tracked file, so the newly authorized machine needs a
        // fresh copy it can actually decrypt.
        manifestPort.save(manifest);
    }
}
