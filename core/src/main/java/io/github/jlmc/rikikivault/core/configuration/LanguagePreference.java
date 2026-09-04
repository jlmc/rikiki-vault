package io.github.jlmc.rikikivault.core.configuration;

import java.util.Objects;

/**
 * Which language is active - machine-global, shared between the GUI and the CLI (Milestone 20),
 * so switching it in one place keeps both consistent on the same machine.
 */
public record LanguagePreference(AppLanguage language) {

    public LanguagePreference {
        Objects.requireNonNull(language, "language must not be null");
    }

    public static LanguagePreference defaultPreference() {
        return new LanguagePreference(AppLanguage.PT);
    }
}
