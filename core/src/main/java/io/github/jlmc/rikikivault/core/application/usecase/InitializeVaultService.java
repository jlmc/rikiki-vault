package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.InitializeMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Composes the vault-level bootstrap (Plan.md §9, steps 5-8) on top of the machine-identity
 * bootstrap (steps 1-4, already covered by {@link InitializeMachineIdentityUseCase}). {@code
 * documents/} and {@code local/} are deliberately not created eagerly: git does not track empty
 * directories, and {@link FileStoragePort#writeFile} brings each one into existence the moment
 * the first file lands in it, exactly like a real git repository behaves.
 */
public final class InitializeVaultService implements InitializeVaultUseCase {

    private static final String GITIGNORE_CONTENT = "local/\n";

    private final InitializeMachineIdentityUseCase initializeMachineIdentityUseCase;
    private final FileStoragePort vaultRootFiles;
    private final ManifestPort manifestPort;
    private final GitRepositoryPort gitRepositoryPort;

    public InitializeVaultService(
            InitializeMachineIdentityUseCase initializeMachineIdentityUseCase,
            FileStoragePort vaultRootFiles,
            ManifestPort manifestPort,
            GitRepositoryPort gitRepositoryPort) {
        this.initializeMachineIdentityUseCase = Objects.requireNonNull(
                initializeMachineIdentityUseCase, "initializeMachineIdentityUseCase must not be null");
        this.vaultRootFiles = Objects.requireNonNull(vaultRootFiles, "vaultRootFiles must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
    }

    @Override
    public MachineIdentity initialize(InitializeVaultCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // Throws MachineIdentityAlreadyExistsException on a repeat call, which doubles as this
        // vault's own "already initialized" guard - nothing below runs in that case.
        MachineIdentity identity = initializeMachineIdentityUseCase.initialize();

        if (command.initializeGitRepository()) {
            gitRepositoryPort.init();
        }
        vaultRootFiles.writeFile(".gitignore", GITIGNORE_CONTENT.getBytes(StandardCharsets.UTF_8));
        manifestPort.save(VaultManifest.empty());

        return identity;
    }
}
