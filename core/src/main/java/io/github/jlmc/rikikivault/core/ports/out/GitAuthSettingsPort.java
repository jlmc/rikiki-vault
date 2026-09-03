package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;

public interface GitAuthSettingsPort {

    GitAuthSettings load();

    void save(GitAuthSettings settings);
}
