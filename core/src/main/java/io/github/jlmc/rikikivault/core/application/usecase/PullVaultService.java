package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import io.github.jlmc.rikikivault.core.domain.model.VaultConflict;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.in.PullVaultUseCase;
import io.github.jlmc.rikikivault.core.ports.in.ScanChangesUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the "pull" flow: pull the encrypted repository, then decide per path
 * whether the remote change can be safely applied to {@code local/}. The manifest travels inside
 * the git repository itself, so {@link GitRepositoryPort#pull()} already updates it - comparing
 * the manifest before/after tells us what changed remotely; comparing that against a pre-pull
 * {@link ScanChangesUseCase#scan()} tells us what also changed locally. A path that changed on
 * both sides is a conflict and is left untouched - the next {@code scan()} will
 * keep reporting it as a local modification, which is the correct and already-existing behavior.
 */
public final class PullVaultService implements PullVaultUseCase {

    private static final Logger log = LoggerFactory.getLogger(PullVaultService.class);

    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final DecryptFileUseCase decryptFileUseCase;
    private final ScanChangesUseCase scanChangesUseCase;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final ManifestPort manifestPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final RecipientRegistryPort recipientRegistryPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public PullVaultService(
            LoadMachineIdentityUseCase loadMachineIdentityUseCase,
            DecryptFileUseCase decryptFileUseCase,
            ScanChangesUseCase scanChangesUseCase,
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            ManifestPort manifestPort,
            GitRepositoryPort gitRepositoryPort,
            RecipientRegistryPort recipientRegistryPort) {
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
        this.decryptFileUseCase = Objects.requireNonNull(decryptFileUseCase, "decryptFileUseCase must not be null");
        this.scanChangesUseCase = Objects.requireNonNull(scanChangesUseCase, "scanChangesUseCase must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
        this.recipientRegistryPort = Objects.requireNonNull(recipientRegistryPort, "recipientRegistryPort must not be null");
    }

    @Override
    public PullResult pull() {
        List<VaultChange> localChanges = scanChangesUseCase.scan();
        Map<String, ChangeType> localChangeTypesByPath = new LinkedHashMap<>();
        for (VaultChange change : localChanges) {
            localChangeTypesByPath.put(change.path(), change.type());
        }
        VaultManifest beforeManifest = manifestPort.load();
        Map<String, ManifestEntry> before = indexByPlaintextPath(beforeManifest);
        Map<String, Recipient> recipientsBefore = indexByFingerprint(recipientRegistryPort.load());

        gitRepositoryPort.pull();

        VaultManifest afterManifest = manifestPort.load();
        Map<String, ManifestEntry> after = indexByPlaintextPath(afterManifest);
        Map<String, Recipient> recipientsAfter = indexByFingerprint(recipientRegistryPort.load());
        MachineIdentity identity = loadMachineIdentityUseCase.load();

        List<String> updatedPaths = new ArrayList<>();
        List<String> deletedPaths = new ArrayList<>();
        List<VaultConflict> conflicts = new ArrayList<>();

        for (ManifestEntry afterEntry : after.values()) {
            String path = afterEntry.plaintextPath();
            ManifestEntry beforeEntry = before.get(path);
            boolean remoteChanged = beforeEntry == null || !beforeEntry.hash().equals(afterEntry.hash());
            if (!remoteChanged) {
                continue;
            }
            ChangeType localType = localChangeTypesByPath.get(path);
            if (localType != null) {
                conflicts.add(new VaultConflict(path, localType, beforeEntry == null ? ChangeType.ADDED : ChangeType.MODIFIED,
                        hashLocalFileIfPresent(path, localType, afterManifest.hmacKey()), afterEntry.hash()));
            } else {
                decryptAndWrite(afterEntry, identity);
                updatedPaths.add(path);
            }
        }

        for (ManifestEntry beforeEntry : before.values()) {
            String path = beforeEntry.plaintextPath();
            if (after.containsKey(path)) {
                continue;
            }
            ChangeType localType = localChangeTypesByPath.get(path);
            if (localType != null) {
                conflicts.add(new VaultConflict(path, localType, ChangeType.DELETED,
                        hashLocalFileIfPresent(path, localType, beforeManifest.hmacKey()), null));
            } else {
                localFiles.deleteFile(path);
                deletedPaths.add(path);
            }
        }

        List<Recipient> newRecipients = new ArrayList<>();
        for (Recipient recipient : recipientsAfter.values()) {
            if (!recipientsBefore.containsKey(recipient.fingerprint().hex())) {
                newRecipients.add(recipient);
            }
        }
        List<Recipient> removedRecipients = new ArrayList<>();
        for (Recipient recipient : recipientsBefore.values()) {
            if (!recipientsAfter.containsKey(recipient.fingerprint().hex())) {
                removedRecipients.add(recipient);
            }
        }

        log.info("Pull completed: {} updated, {} deleted, {} conflict(s), {} new recipient(s), {} removed recipient(s)",
                updatedPaths.size(), deletedPaths.size(), conflicts.size(), newRecipients.size(), removedRecipients.size());
        return new PullResult(updatedPaths, deletedPaths, conflicts, localChanges, newRecipients, removedRecipients);
    }

    private FileHash hashLocalFileIfPresent(String path, ChangeType localType, byte[] hmacKey) {
        // A DELETED local change means there's no local content left to hash (only
        // computes a hash of whichever side still has a version to compare).
        if (localType == ChangeType.DELETED) {
            return null;
        }
        return FileHash.hmac(hmacKey, localFiles.readFile(path));
    }

    private void decryptAndWrite(ManifestEntry entry, MachineIdentity identity) {
        EncryptedFile encryptedFile = codec.decode(documentsFiles.readFile(entry.documentsRelativePath()));
        PlaintextFile plaintext = decryptFileUseCase.decrypt(new DecryptFileCommand(encryptedFile, identity));
        localFiles.writeFile(entry.plaintextPath(), plaintext.content());
    }

    private static Map<String, ManifestEntry> indexByPlaintextPath(VaultManifest manifest) {
        Map<String, ManifestEntry> byPath = new LinkedHashMap<>();
        for (ManifestEntry entry : manifest.files()) {
            byPath.put(entry.plaintextPath(), entry);
        }
        return byPath;
    }

    private static Map<String, Recipient> indexByFingerprint(RecipientRegistry registry) {
        Map<String, Recipient> byFingerprint = new LinkedHashMap<>();
        for (Recipient recipient : registry.recipients()) {
            byFingerprint.put(recipient.fingerprint().hex(), recipient);
        }
        return byFingerprint;
    }
}
