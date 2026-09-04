package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.configuration.LanguagePreference;

public interface LanguagePreferencePort {

    LanguagePreference load();

    void save(LanguagePreference preference);
}
