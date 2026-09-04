package io.github.jlmc.rikikivault.gui;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against the two message bundles drifting apart - a key present in one language but not
 * the other would silently fall back to a {@code MissingResourceException} at runtime instead of
 * failing a build.
 */
class MessagesTest {

    @Test
    void ptAndEnBundlesHaveExactlyTheSameKeys() {
        Set<String> ptKeys = keysOf("pt");
        Set<String> enKeys = keysOf(Locale.ENGLISH.toLanguageTag());

        assertFalse(ptKeys.isEmpty());
        assertEquals(ptKeys, enKeys);
    }

    @Test
    void noValueIsBlank() {
        for (String tag : new String[]{"pt", "en"}) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(tag));
            for (String key : bundle.keySet()) {
                assertFalse(bundle.getString(key).isBlank(), "empty value for key " + key + " (" + tag + ")");
            }
        }
    }

    @Test
    void aKnownKeyResolvesInBothLanguages() {
        assertTrue(ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("pt")).getString("common.close").length() > 0);
        assertTrue(ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH).getString("common.close").length() > 0);
    }

    private static Set<String> keysOf(String languageTag) {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(languageTag));
        return new TreeSet<>(bundle.keySet());
    }
}
