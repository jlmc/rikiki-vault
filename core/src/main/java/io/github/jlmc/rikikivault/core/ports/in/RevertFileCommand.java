package io.github.jlmc.rikikivault.core.ports.in;

import java.util.Objects;

public record RevertFileCommand(String plaintextPath) {

    public RevertFileCommand {
        Objects.requireNonNull(plaintextPath, "plaintextPath must not be null");
        if (plaintextPath.isBlank()) {
            throw new IllegalArgumentException("plaintextPath must not be blank");
        }
    }
}
