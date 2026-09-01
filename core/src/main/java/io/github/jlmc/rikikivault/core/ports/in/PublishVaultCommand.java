package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;

import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

public record PublishVaultCommand(List<VaultChange> approvedChanges, List<PublicKey> recipients, String commitMessage) {

    public PublishVaultCommand {
        Objects.requireNonNull(approvedChanges, "approvedChanges must not be null");
        Objects.requireNonNull(recipients, "recipients must not be null");
        Objects.requireNonNull(commitMessage, "commitMessage must not be null");
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("recipients must not be empty");
        }
        if (commitMessage.isBlank()) {
            throw new IllegalArgumentException("commitMessage must not be blank");
        }
        approvedChanges = List.copyOf(approvedChanges);
        recipients = List.copyOf(recipients);
    }
}
