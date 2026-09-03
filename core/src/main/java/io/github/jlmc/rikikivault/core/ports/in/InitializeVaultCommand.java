package io.github.jlmc.rikikivault.core.ports.in;

import java.util.Objects;

/**
 * {@code remoteUri} is optional (may be {@code null} or blank) - when present, it only makes
 * sense together with {@code initializeGitRepository}, since without a local Git repository there
 * is nothing to associate a remote with.
 */
public record InitializeVaultCommand(boolean initializeGitRepository, String machineLabel, String remoteUri) {

    public InitializeVaultCommand {
        Objects.requireNonNull(machineLabel, "machineLabel must not be null");
        if (machineLabel.isBlank()) {
            throw new IllegalArgumentException("machineLabel must not be blank");
        }
    }
}
