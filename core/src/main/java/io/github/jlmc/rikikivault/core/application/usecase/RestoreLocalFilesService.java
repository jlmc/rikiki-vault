package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.RestoreLocalFilesResult;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.in.RestoreLocalFilesCommand;
import io.github.jlmc.rikikivault.core.ports.in.RestoreLocalFilesUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reconstructs {@code local/} from the already-published {@code documents/} content - purely
 * local, no Git/network involved. Unlike {@link PullVaultService}, which only re-decrypts entries
 * whose manifest hash changed since the last pull, this walks the whole manifest every time,
 * which is what makes it able to recover from {@code local/} having been deleted (accidentally or
 * otherwise) without anything having changed on the remote.
 */
public final class RestoreLocalFilesService implements RestoreLocalFilesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RestoreLocalFilesService.class);

    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final DecryptFileUseCase decryptFileUseCase;
    private final FileStoragePort localFiles;
    private final FileStoragePort documentsFiles;
    private final ManifestPort manifestPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public RestoreLocalFilesService(
            LoadMachineIdentityUseCase loadMachineIdentityUseCase,
            DecryptFileUseCase decryptFileUseCase,
            FileStoragePort localFiles,
            FileStoragePort documentsFiles,
            ManifestPort manifestPort) {
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
        this.decryptFileUseCase = Objects.requireNonNull(decryptFileUseCase, "decryptFileUseCase must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
    }

    @Override
    public RestoreLocalFilesResult restore(RestoreLocalFilesCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        MachineIdentity identity = loadMachineIdentityUseCase.load();
        Set<String> existingLocalPaths = Set.copyOf(localFiles.listFiles());

        List<String> restored = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> unauthorized = new ArrayList<>();

        for (ManifestEntry entry : manifestPort.load().files()) {
            String plaintextPath = entry.plaintextPath();
            if (existingLocalPaths.contains(plaintextPath) && !command.force()) {
                skipped.add(plaintextPath);
                continue;
            }
            try {
                EncryptedFile encryptedFile = codec.decode(documentsFiles.readFile(entry.path()));
                PlaintextFile plaintext = decryptFileUseCase.decrypt(new DecryptFileCommand(encryptedFile, identity));
                localFiles.writeFile(plaintextPath, plaintext.content());
                restored.add(plaintextPath);
            } catch (UnauthorizedMachineException e) {
                unauthorized.add(plaintextPath);
            }
        }

        log.info("Restore completed: {} restored, {} already present, {} not authorized",
                restored.size(), skipped.size(), unauthorized.size());
        return new RestoreLocalFilesResult(restored, skipped, unauthorized);
    }
}
