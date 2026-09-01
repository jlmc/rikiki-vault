package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;

public interface DecryptFileUseCase {

    PlaintextFile decrypt(DecryptFileCommand command);
}
