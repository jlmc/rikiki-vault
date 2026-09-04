package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultUseCase;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.HashPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the "encrypt & push" flow for a batch of approved changes.
 * Recipients are resolved from the vault's
 * {@link RecipientRegistryPort} rather than supplied by the caller, so a publish can
 * never accidentally omit an authorized machine.
 */
public final class PublishVaultService implements PublishVaultUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishVaultService.class);

    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final EncryptionPort encryptionPort;
    private final HashPort hashPort;
    private final ManifestPort manifestPort;
    private final RecipientRegistryPort recipientRegistryPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public PublishVaultService(
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            EncryptionPort encryptionPort,
            HashPort hashPort,
            ManifestPort manifestPort,
            RecipientRegistryPort recipientRegistryPort,
            GitRepositoryPort gitRepositoryPort) {
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.encryptionPort = Objects.requireNonNull(encryptionPort, "encryptionPort must not be null");
        this.hashPort = Objects.requireNonNull(hashPort, "hashPort must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.recipientRegistryPort = Objects.requireNonNull(recipientRegistryPort, "recipientRegistryPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
    }

    @Override
    public boolean publish(PublishVaultCommand command) {
        publishLocally(command);
        return pushToRemote();
    }

    @Override
    public void publishLocally(PublishVaultCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        List<PublicKey> recipients = recipientRegistryPort.load().recipients().stream().map(Recipient::publicKey).toList();

        VaultManifest manifest = manifestPort.load();
        Map<String, ManifestEntry> entriesByPlaintextPath = new LinkedHashMap<>();
        for (ManifestEntry entry : manifest.files()) {
            entriesByPlaintextPath.put(entry.plaintextPath(), entry);
        }

        for (VaultChange change : command.approvedChanges()) {
            switch (change.type()) {
                case ADDED, MODIFIED -> entriesByPlaintextPath.put(
                        change.path(), encryptAndStore(change.path(), recipients));
                case DELETED -> {
                    ManifestEntry removed = entriesByPlaintextPath.remove(change.path());
                    if (removed != null) {
                        documentsFiles.deleteFile(removed.path());
                    }
                }
            }
        }

        manifestPort.save(new VaultManifest(manifest.version(), List.copyOf(entriesByPlaintextPath.values())));

        gitRepositoryPort.add(List.of("."));
        gitRepositoryPort.commit(command.commitMessage());
        log.info("Published {} change(s) locally: \"{}\"", command.approvedChanges().size(), command.commitMessage());
    }

    @Override
    public boolean pushToRemote() {
        boolean pushed = gitRepositoryPort.push();
        log.info(pushed ? "Pushed the published changes to the remote" : "No remote configured - changes stayed local only");
        return pushed;
    }

    private ManifestEntry encryptAndStore(String plaintextPath, List<PublicKey> recipients) {
        byte[] content = localFiles.readFile(plaintextPath);
        String fileName = plaintextPath.contains("/")
                ? plaintextPath.substring(plaintextPath.lastIndexOf('/') + 1)
                : plaintextPath;

        EncryptedFile encrypted = encryptionPort.encrypt(new PlaintextFile(fileName, content), recipients);
        String encryptedPath = plaintextPath + ".enc";
        documentsFiles.writeFile(encryptedPath, codec.encode(encrypted));

        return new ManifestEntry(encryptedPath, plaintextPath, hashPort.hash(content), RvEncryptedFileFormatCodec.FORMAT_VERSION);
    }
}
