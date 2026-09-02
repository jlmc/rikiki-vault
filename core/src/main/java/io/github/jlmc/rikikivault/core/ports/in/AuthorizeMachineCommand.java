package io.github.jlmc.rikikivault.core.ports.in;

import java.security.PublicKey;
import java.util.Objects;

public record AuthorizeMachineCommand(String label, PublicKey publicKey) {

    public AuthorizeMachineCommand {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
