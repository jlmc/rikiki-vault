package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.LanguagePreference;
import io.github.jlmc.rikikivault.core.ports.out.LanguagePreferencePort;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Narrow {@link LanguagePreferencePort} view over the shared {@code preferences.json} file
 * (Milestone 19) - delegates all actual persistence to {@link LocalAppPreferencesAdapter}, only
 * ever touching the {@code language} part of {@link AppPreferences}.
 */
public final class LocalLanguagePreferenceAdapter implements LanguagePreferencePort {

    private final LocalAppPreferencesAdapter preferencesAdapter;

    public LocalLanguagePreferenceAdapter(Path preferencesDirectory) {
        this(new LocalAppPreferencesAdapter(Objects.requireNonNull(preferencesDirectory, "preferencesDirectory must not be null")));
    }

    /** Test-only seam - lets tests inject a {@link LocalAppPreferencesAdapter} that never
     * consults this machine's real legacy git-auth file. */
    LocalLanguagePreferenceAdapter(LocalAppPreferencesAdapter preferencesAdapter) {
        this.preferencesAdapter = preferencesAdapter;
    }

    @Override
    public LanguagePreference load() {
        return new LanguagePreference(preferencesAdapter.load().language());
    }

    @Override
    public void save(LanguagePreference preference) {
        Objects.requireNonNull(preference, "preference must not be null");
        AppPreferences current = preferencesAdapter.load();
        preferencesAdapter.save(new AppPreferences(current.gitAuth(), preference.language(), current.notifications()));
    }
}
