package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.RestoreLocalFilesResult;

public interface RestoreLocalFilesUseCase {

    RestoreLocalFilesResult restore(RestoreLocalFilesCommand command);
}
