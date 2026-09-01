package io.github.jlmc.rikikivault.core.ports.in;

import java.util.Objects;

public record CloneVaultCommand(String remoteUri) {

    public CloneVaultCommand {
        Objects.requireNonNull(remoteUri, "remoteUri must not be null");
        if (remoteUri.isBlank()) {
            throw new IllegalArgumentException("remoteUri must not be blank");
        }
    }
}
