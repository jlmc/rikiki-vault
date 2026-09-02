package io.github.jlmc.rikikivault.core.ports.in;

import java.util.Objects;

public record InitializeVaultCommand(boolean initializeGitRepository, String machineLabel) {

    public InitializeVaultCommand {
        Objects.requireNonNull(machineLabel, "machineLabel must not be null");
        if (machineLabel.isBlank()) {
            throw new IllegalArgumentException("machineLabel must not be blank");
        }
    }
}
