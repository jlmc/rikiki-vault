package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;

import java.util.Objects;

public record RevokeMachineCommand(KeyFingerprint fingerprint) {

    public RevokeMachineCommand {
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    }
}
