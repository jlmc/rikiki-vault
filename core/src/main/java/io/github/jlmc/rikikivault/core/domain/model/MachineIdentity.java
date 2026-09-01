package io.github.jlmc.rikikivault.core.domain.model;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

public record MachineIdentity(KeyFingerprint id, PublicKey publicKey, PrivateKey privateKey, String keyAlgorithm) {

    public MachineIdentity {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(keyAlgorithm, "keyAlgorithm must not be null");
    }

    @Override
    public String toString() {
        return "MachineIdentity[id=" + id + ", keyAlgorithm=" + keyAlgorithm + "]";
    }
}
