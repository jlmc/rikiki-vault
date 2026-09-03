package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
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
import io.github.jlmc.rikikivault.core.ports.out.HashPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the "pull" flow (Plan.md §11): pull the encrypted repository, then decide per path
 * whether the remote change can be safely applied to {@code local/}. The manifest travels inside
 * the git repository itself, so {@link GitRepositoryPort#pull()} already updates it - comparing
 * the manifest before/after tells us what changed remotely; comparing that against a pre-pull
 * {@link ScanChangesUseCase#scan()} tells us what also changed locally. A path that changed on
 * both sides is a conflict and is left untouched (Plan.md §11/§32) - the next {@code scan()} will
 * keep reporting it as a local modification, which is the correct and already-existing behavior.
 */
public final class PullVaultService implements PullVaultUseCase {

    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final DecryptFileUseCase decryptFileUseCase;
    private final ScanChangesUseCase scanChangesUseCase;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final ManifestPort manifestPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final HashPort hashPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public PullVaultService(
            LoadMachineIdentityUseCase loadMachineIdentityUseCase,
            DecryptFileUseCase decryptFileUseCase,
            ScanChangesUseCase scanChangesUseCase,
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            ManifestPort manifestPort,
            GitRepositoryPort gitRepositoryPort,
            HashPort hashPort) {
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
        this.decryptFileUseCase = Objects.requireNonNull(decryptFileUseCase, "decryptFileUseCase must not be null");
        this.scanChangesUseCase = Objects.requireNonNull(scanChangesUseCase, "scanChangesUseCase must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
        this.hashPort = Objects.requireNonNull(hashPort, "hashPort must not be null");
    }

    @Override
    public PullResult pull() {
        List<VaultChange> localChanges = scanChangesUseCase.scan();
        Map<String, ChangeType> localChangeTypesByPath = new LinkedHashMap<>();
        for (VaultChange change : localChanges) {
            localChangeTypesByPath.put(change.path(), change.type());
        }
        Map<String, ManifestEntry> before = indexByPlaintextPath(manifestPort.load());

        gitRepositoryPort.pull();

        Map<String, ManifestEntry> after = indexByPlaintextPath(manifestPort.load());
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
                        hashLocalFileIfPresent(path, localType), afterEntry.hash()));
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
                        hashLocalFileIfPresent(path, localType), null));
            } else {
                localFiles.deleteFile(path);
                deletedPaths.add(path);
            }
        }

        return new PullResult(updatedPaths, deletedPaths, conflicts, localChanges);
    }

    private FileHash hashLocalFileIfPresent(String path, ChangeType localType) {
        // A DELETED local change means there's no local content left to hash (Plan.md §32 only
        // asks for a hash of whichever side still has a version to compare).
        if (localType == ChangeType.DELETED) {
            return null;
        }
        return hashPort.hash(localFiles.readFile(path));
    }

    private void decryptAndWrite(ManifestEntry entry, MachineIdentity identity) {
        EncryptedFile encryptedFile = codec.decode(documentsFiles.readFile(entry.path()));
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
}
