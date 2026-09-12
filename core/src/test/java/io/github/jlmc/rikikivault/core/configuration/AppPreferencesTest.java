package io.github.jlmc.rikikivault.core.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppPreferencesTest {

    @Test
    void emptyHasDefaultGitAuthAndPortugueseLanguage() {
        AppPreferences preferences = AppPreferences.empty();

        assertEquals(GitAuthSettings.empty(), preferences.gitAuth());
        assertEquals(AppLanguage.PT, preferences.language());
        assertEquals(NotificationSettings.empty(), preferences.notifications());
    }

    @Test
    void rejectsNullFields() {
        assertThrows(NullPointerException.class, () -> new AppPreferences(null, AppLanguage.PT, NotificationSettings.empty()));
        assertThrows(NullPointerException.class, () -> new AppPreferences(GitAuthSettings.empty(), null, NotificationSettings.empty()));
        assertThrows(NullPointerException.class, () -> new AppPreferences(GitAuthSettings.empty(), AppLanguage.PT, null));
    }
}
