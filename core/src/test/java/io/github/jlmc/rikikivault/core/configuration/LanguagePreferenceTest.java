package io.github.jlmc.rikikivault.core.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanguagePreferenceTest {

    @Test
    void defaultPreferenceIsPortuguese() {
        assertEquals(AppLanguage.PT, LanguagePreference.defaultPreference().language());
    }

    @Test
    void rejectsNullLanguage() {
        assertThrows(NullPointerException.class, () -> new LanguagePreference(null));
    }
}
