package io.github.jlmc.rikikivault.core.configuration;

import java.util.Objects;

/**
 * Everything the Settings screen (Milestone 19) manages, persisted together as a single file
 * instead of one file per concern - {@link GitAuthSettings} and the active {@link AppLanguage}
 * used to live in separate files (git-auth/settings.json, preferences/settings.json); unifying
 * them means the Settings screen's Save button only ever writes one place. Deliberately excludes
 * {@link VaultConfig} - that's bootstrap configuration for the vault/identity mechanics, not a
 * user-facing preference, and keeps its own {@code config.yaml}.
 */
public record AppPreferences(GitAuthSettings gitAuth, AppLanguage language) {

    public AppPreferences {
        Objects.requireNonNull(gitAuth, "gitAuth must not be null");
        Objects.requireNonNull(language, "language must not be null");
    }

    public static AppPreferences empty() {
        return new AppPreferences(GitAuthSettings.empty(), AppLanguage.PT);
    }
}
