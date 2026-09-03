package io.github.jlmc.rikikivault.core.adapters.git;

import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.ports.out.GitAuthSettingsPort;

/** In-memory {@link GitAuthSettingsPort} for tests that construct a real {@link JGitRepositoryAdapter}. */
public final class FakeGitAuthSettingsPort implements GitAuthSettingsPort {

    private GitAuthSettings settings = GitAuthSettings.empty();

    @Override
    public GitAuthSettings load() {
        return settings;
    }

    @Override
    public void save(GitAuthSettings settings) {
        this.settings = settings;
    }
}
