package io.github.jlmc.rikikivault.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of migrating a pre-RV02 vault (plaintext {@code manifest.json}, {@code
 * documents/&lt;real-path&gt;.enc} filenames) to the opaque-path format: {@code manifest.json}
 * becomes itself encrypted and every {@code documents/} filename becomes an unguessable id. {@code
 * alreadyMigrated} short-circuits a re-run against a vault that's already on the new format.
 */
public record MigrateVaultFormatResult(
        boolean alreadyMigrated, boolean dryRun, int filesMigrated, List<String> failedPaths, boolean pushed) {

    public MigrateVaultFormatResult {
        Objects.requireNonNull(failedPaths, "failedPaths must not be null");
        failedPaths = List.copyOf(failedPaths);
    }

    public static MigrateVaultFormatResult ofAlreadyMigrated() {
        return new MigrateVaultFormatResult(true, false, 0, List.of(), false);
    }
}
