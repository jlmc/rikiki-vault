package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;

import java.util.List;
import java.util.Objects;

public record PublishVaultCommand(List<VaultChange> approvedChanges, String commitMessage) {

    public PublishVaultCommand {
        Objects.requireNonNull(approvedChanges, "approvedChanges must not be null");
        Objects.requireNonNull(commitMessage, "commitMessage must not be null");
        if (commitMessage.isBlank()) {
            throw new IllegalArgumentException("commitMessage must not be blank");
        }
        approvedChanges = List.copyOf(approvedChanges);
    }
}
