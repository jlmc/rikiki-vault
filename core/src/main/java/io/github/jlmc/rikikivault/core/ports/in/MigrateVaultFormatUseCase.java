package io.github.jlmc.rikikivault.core.ports.in;

import io.github.jlmc.rikikivault.core.domain.model.MigrateVaultFormatResult;

public interface MigrateVaultFormatUseCase {

    MigrateVaultFormatResult migrate(MigrateVaultFormatCommand command);
}
