package io.github.jlmc.core.ports.in;

import io.github.jlmc.core.domain.model.EncryptedFile;

public interface EncryptFileUseCase {

    EncryptedFile encrypt(EncryptFileCommand command);
}
