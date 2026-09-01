package io.github.jlmc.core.ports.in;

import io.github.jlmc.core.domain.model.EncryptedFile;
import io.github.jlmc.core.domain.model.MachineIdentity;

import java.util.Objects;

public record DecryptFileCommand(EncryptedFile file, MachineIdentity identity) {

    public DecryptFileCommand {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(identity, "identity must not be null");
    }
}
