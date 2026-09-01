package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultUseCase;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.util.Objects;

/**
 * Composes a new machine joining an existing vault (Plan.md §10): load this machine's already
 * generated identity (step 4 - the identity is expected to already exist, e.g. generated ahead of
 * time so its public key could be shared with whoever manages recipient authorization), clone the
 * encrypted repository, then decrypt every manifest entry into {@code local/}. Loading the
 * manifest doubles as "validate repository structure" (step 2) - it fails on its own if the clone
 * did not produce a readable one. Recipient authorization (step 5) is enforced by
 * {@link DecryptFileUseCase}, not duplicated here.
 */
public final class CloneVaultService implements CloneVaultUseCase {

    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final DecryptFileUseCase decryptFileUseCase;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final ManifestPort manifestPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public CloneVaultService(
            LoadMachineIdentityUseCase loadMachineIdentityUseCase,
            DecryptFileUseCase decryptFileUseCase,
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            ManifestPort manifestPort,
            GitRepositoryPort gitRepositoryPort) {
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
        this.decryptFileUseCase = Objects.requireNonNull(decryptFileUseCase, "decryptFileUseCase must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
    }

    @Override
    public MachineIdentity clone(CloneVaultCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        MachineIdentity identity = loadMachineIdentityUseCase.load();

        gitRepositoryPort.clone(command.remoteUri());

        VaultManifest manifest = manifestPort.load();
        for (ManifestEntry entry : manifest.files()) {
            decryptAndWrite(entry, identity);
        }

        return identity;
    }

    private void decryptAndWrite(ManifestEntry entry, MachineIdentity identity) {
        EncryptedFile encryptedFile = codec.decode(documentsFiles.readFile(entry.path()));
        PlaintextFile plaintext = decryptFileUseCase.decrypt(new DecryptFileCommand(encryptedFile, identity));
        localFiles.writeFile(entry.plaintextPath(), plaintext.content());
    }
}
