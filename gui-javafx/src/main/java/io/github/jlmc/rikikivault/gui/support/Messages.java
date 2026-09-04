package io.github.jlmc.rikikivault.gui.support;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalLanguagePreferenceAdapter;
import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.LanguagePreferencePort;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Central lookup for user-facing text (Milestone 20), resolved against the language configured
 * in Settings ({@link LanguagePreferencePort}). Re-reads the preference on every call rather than
 * caching it, so a language change takes effect the moment a screen is next loaded - this app's
 * scale makes that cost negligible.
 */
public final class Messages {

    private static final String BUNDLE_BASE_NAME = "i18n.messages";
    private static final LanguagePreferencePort LANGUAGE_PORT =
            new LocalLanguagePreferenceAdapter(VaultPaths.defaultPreferencesDirectory());

    private Messages() {
    }

    static ResourceBundle bundle() {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, localeFor(LANGUAGE_PORT.load().language()));
    }

    public static String get(String key, Object... args) {
        String pattern = bundle().getString(key);
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }

    private static Locale localeFor(AppLanguage language) {
        return language == AppLanguage.EN ? Locale.ENGLISH : Locale.of("pt");
    }
}
