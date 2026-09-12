package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.in.DiffFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DiffFileUseCase;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.out.DiffPort;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.util.Objects;
import java.util.Optional;

/**
 * Compares the current (possibly unsaved) editor content of a file against its last-published
 * version. A path never published compares against
 * empty content - the whole file shows up as added, which is the honest answer for "what would
 * change if I published this now".
 */
public final class DiffFileService implements DiffFileUseCase {

    private final ManifestPort manifestPort;
    private final FileStoragePort documentsFiles;
    private final DecryptFileUseCase decryptFileUseCase;
    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final DiffPort diffPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public DiffFileService(
            ManifestPort manifestPort,
            FileStoragePort documentsFiles,
            DecryptFileUseCase decryptFileUseCase,
            LoadMachineIdentityUseCase loadMachineIdentityUseCase,
            DiffPort diffPort) {
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.decryptFileUseCase = Objects.requireNonNull(decryptFileUseCase, "decryptFileUseCase must not be null");
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
        this.diffPort = Objects.requireNonNull(diffPort, "diffPort must not be null");
    }

    @Override
    public String diff(DiffFileCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        VaultManifest manifest = manifestPort.load();
        Optional<ManifestEntry> entry = manifest.files().stream()
                .filter(candidate -> candidate.plaintextPath().equals(command.plaintextPath()))
                .findFirst();

        byte[] previousContent = entry.isPresent() ? decryptPublished(entry.get()) : new byte[0];

        return diffPort.diff(previousContent, command.currentContent());
    }

    private byte[] decryptPublished(ManifestEntry entry) {
        MachineIdentity identity = loadMachineIdentityUseCase.load();
        EncryptedFile encryptedFile = codec.decode(documentsFiles.readFile(entry.documentsRelativePath()));
        PlaintextFile plaintext = decryptFileUseCase.decrypt(new DecryptFileCommand(encryptedFile, identity));
        return plaintext.content();
    }
}
