package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;

public interface EncryptFileUseCase {

    EncryptedFile encrypt(EncryptFileCommand command);
}
