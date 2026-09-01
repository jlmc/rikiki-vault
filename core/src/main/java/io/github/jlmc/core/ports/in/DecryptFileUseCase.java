package io.github.jlmc.core.ports.in;

import io.github.jlmc.core.domain.model.PlaintextFile;

public interface DecryptFileUseCase {

    PlaintextFile decrypt(DecryptFileCommand command);
}
