package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.ClearLocalFilesResult;

public interface ClearLocalFilesUseCase {

    ClearLocalFilesResult clear(ClearLocalFilesCommand command);
}
