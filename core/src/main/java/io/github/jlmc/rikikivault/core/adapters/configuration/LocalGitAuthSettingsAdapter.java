package io.github.jlmc.rikikivault.core.adapters.configuration;

import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.ports.out.GitAuthSettingsPort;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Narrow {@link GitAuthSettingsPort} view over the shared {@code preferences.json} file
 * (Milestone 19) - delegates all actual persistence to {@link LocalAppPreferencesAdapter}, only
 * ever touching the {@code gitAuth} part of {@link AppPreferences} so callers that only need Git
 * credentials (like {@code JGitRepositoryAdapter}) stay unaware of the language preference living
 * in the same file.
 */
public final class LocalGitAuthSettingsAdapter implements GitAuthSettingsPort {

    private final LocalAppPreferencesAdapter preferencesAdapter;

    public LocalGitAuthSettingsAdapter(Path preferencesDirectory) {
        this(new LocalAppPreferencesAdapter(Objects.requireNonNull(preferencesDirectory, "preferencesDirectory must not be null")));
    }

    /** Test-only seam - lets tests inject a {@link LocalAppPreferencesAdapter} that never
     * consults this machine's real legacy git-auth file. */
    LocalGitAuthSettingsAdapter(LocalAppPreferencesAdapter preferencesAdapter) {
        this.preferencesAdapter = preferencesAdapter;
    }

    @Override
    public GitAuthSettings load() {
        return preferencesAdapter.load().gitAuth();
    }

    @Override
    public void save(GitAuthSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        AppPreferences current = preferencesAdapter.load();
        preferencesAdapter.save(new AppPreferences(settings, current.language()));
    }
}
