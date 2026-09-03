package io.github.jlmc.rikikivault.core.ports.in;

import java.util.Objects;

public record DiffFileCommand(String plaintextPath, byte[] currentContent) {

    public DiffFileCommand {
        Objects.requireNonNull(plaintextPath, "plaintextPath must not be null");
        Objects.requireNonNull(currentContent, "currentContent must not be null");
        if (plaintextPath.isBlank()) {
            throw new IllegalArgumentException("plaintextPath must not be blank");
        }
    }
}
