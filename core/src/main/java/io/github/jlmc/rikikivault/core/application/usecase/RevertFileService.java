package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.in.RevertFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevertFileUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.util.Objects;

/**
 * Discards local edits by restoring a file to its last-published state ("Revert"
 * editor action). Only meaningful for a file that has actually been published before - a file
 * that only exists locally (never published) has nothing to revert to.
 */
public final class RevertFileService implements RevertFileUseCase {

    private final ManifestPort manifestPort;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final DecryptFileUseCase decryptFileUseCase;
    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public RevertFileService(
            ManifestPort manifestPort,
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            DecryptFileUseCase decryptFileUseCase,
            LoadMachineIdentityUseCase loadMachineIdentityUseCase) {
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.decryptFileUseCase = Objects.requireNonNull(decryptFileUseCase, "decryptFileUseCase must not be null");
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
    }

    @Override
    public void revert(RevertFileCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        VaultManifest manifest = manifestPort.load();
        ManifestEntry entry = manifest.files().stream()
                .filter(candidate -> candidate.plaintextPath().equals(command.plaintextPath()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No published version of " + command.plaintextPath() + " to revert to"));

        MachineIdentity identity = loadMachineIdentityUseCase.load();
        EncryptedFile encryptedFile = codec.decode(documentsFiles.readFile(entry.documentsRelativePath()));
        PlaintextFile plaintext = decryptFileUseCase.decrypt(new DecryptFileCommand(encryptedFile, identity));

        localFiles.writeFile(command.plaintextPath(), plaintext.content());
    }
}
