package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.configuration.AppPreferences;

public interface AppPreferencesPort {

    AppPreferences load();

    void save(AppPreferences preferences);
}
