package io.github.jlmc.rikikivault.core.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GitAuthSettingsTest {

    @Test
    void emptyHasNoActiveTypeAndAllFieldsUnset() {
        GitAuthSettings settings = GitAuthSettings.empty();

        assertEquals(GitAuthType.NONE, settings.activeType());
        assertNull(settings.sshPrivateKeyPath());
        assertNull(settings.githubToken());
        assertNull(settings.httpUsername());
        assertNull(settings.httpPassword());
    }
}
