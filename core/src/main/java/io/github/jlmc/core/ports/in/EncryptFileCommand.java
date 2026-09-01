package io.github.jlmc.core.ports.in;

import io.github.jlmc.core.domain.model.PlaintextFile;

import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

public record EncryptFileCommand(PlaintextFile file, List<PublicKey> recipients) {

    public EncryptFileCommand {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(recipients, "recipients must not be null");
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("recipients must not be empty");
        }
        recipients = List.copyOf(recipients);
    }
}
