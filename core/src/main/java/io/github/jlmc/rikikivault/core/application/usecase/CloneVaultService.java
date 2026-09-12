package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.exception.UninitializedVaultException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultUseCase;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.in.InitializeMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Composes a new machine joining an existing vault. This machine's identity
 * ("load local private key") is reused if it already exists - e.g. generated ahead of time so
 * its public key could be shared with whoever manages recipient authorization - or generated on
 * the spot otherwise, since a genuinely first-time machine has nothing but a folder of files it
 * wants tracked and no key at all yet. "Validate repository structure" checks the
 * recipient registry, not the manifest - {@code ManifestPort#load()} is deliberately fail-safe on
 * a missing file (it means "nothing published yet" for an otherwise-valid vault), so it cannot
 * tell an empty-but-initialized vault apart from a remote that was never initialized at all; a
 * validly initialized vault always has at least one recipient (whoever created it), even with zero
 * files published. Recipient authorization is enforced per file by
 * {@link DecryptFileUseCase}, not duplicated here - a brand-new identity that was not yet granted
 * access to any pre-existing file will simply fail to decrypt it.
 */
public final class CloneVaultService implements CloneVaultUseCase {

    private static final Logger log = LoggerFactory.getLogger(CloneVaultService.class);

    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final InitializeMachineIdentityUseCase initializeMachineIdentityUseCase;
    private final DecryptFileUseCase decryptFileUseCase;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final ManifestPort manifestPort;
    private final RecipientRegistryPort recipientRegistryPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public CloneVaultService(
            LoadMachineIdentityUseCase loadMachineIdentityUseCase,
            InitializeMachineIdentityUseCase initializeMachineIdentityUseCase,
            DecryptFileUseCase decryptFileUseCase,
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            ManifestPort manifestPort,
            RecipientRegistryPort recipientRegistryPort,
            GitRepositoryPort gitRepositoryPort) {
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
        this.initializeMachineIdentityUseCase = Objects.requireNonNull(
                initializeMachineIdentityUseCase, "initializeMachineIdentityUseCase must not be null");
        this.decryptFileUseCase = Objects.requireNonNull(decryptFileUseCase, "decryptFileUseCase must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.recipientRegistryPort = Objects.requireNonNull(recipientRegistryPort, "recipientRegistryPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
    }

    @Override
    public MachineIdentity clone(CloneVaultCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        MachineIdentity identity = loadOrCreateIdentity();

        gitRepositoryPort.clone(command.remoteUri());

        if (recipientRegistryPort.load().recipients().isEmpty()) {
            throw new UninitializedVaultException(
                    "Remote does not look like an initialized Rikiki Vault (no recipients found). "
                            + "Use 'init' to create a brand-new vault instead of 'clone'.");
        }

        VaultManifest manifest;
        try {
            manifest = manifestPort.load();
        } catch (UnauthorizedMachineException e) {
            // The manifest is encrypted like any other tracked file - a freshly cloned machine
            // that hasn't been authorized yet can't decrypt it (so it can't see any real paths
            // either), same as it already couldn't decrypt file content. Not an error: the clone
            // itself succeeded, there's just nothing visible until an existing machine authorizes it.
            log.info("Cloned vault as {}, but this machine is not yet an authorized recipient - "
                    + "0 files visible until an existing machine runs 'authorize'.", identity.id());
            return identity;
        }
        for (ManifestEntry entry : manifest.files()) {
            decryptAndWrite(entry, identity);
        }

        log.info("Cloned vault with {} file(s) as {}", manifest.files().size(), identity.id());
        return identity;
    }

    private MachineIdentity loadOrCreateIdentity() {
        try {
            return loadMachineIdentityUseCase.load();
        } catch (PrivateKeyNotFoundException e) {
            return initializeMachineIdentityUseCase.initialize();
        }
    }

    private void decryptAndWrite(ManifestEntry entry, MachineIdentity identity) {
        EncryptedFile encryptedFile = codec.decode(documentsFiles.readFile(entry.documentsRelativePath()));
        PlaintextFile plaintext = decryptFileUseCase.decrypt(new DecryptFileCommand(encryptedFile, identity));
        localFiles.writeFile(entry.plaintextPath(), plaintext.content());
    }
}
