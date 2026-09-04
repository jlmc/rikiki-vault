package io.github.jlmc.rikikivault.cli;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalLanguagePreferenceAdapter;
import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.LanguagePreferencePort;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Central lookup for CLI text (Milestone 20), resolved against the same
 * {@link LanguagePreferencePort} preference the GUI's Settings screen writes - the two interfaces
 * share one machine-wide language setting, so switching it in the GUI also changes what the CLI
 * prints next time it runs.
 */
final class CliMessages {

    private static final String BUNDLE_BASE_NAME = "i18n.messages";
    private static final LanguagePreferencePort LANGUAGE_PORT =
            new LocalLanguagePreferenceAdapter(VaultPaths.defaultPreferencesDirectory());

    private CliMessages() {
    }

    static String get(String key, Object... args) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, localeFor(LANGUAGE_PORT.load().language()));
        String pattern = bundle.getString(key);
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }

    private static Locale localeFor(AppLanguage language) {
        return language == AppLanguage.EN ? Locale.ENGLISH : Locale.of("pt");
    }
}
