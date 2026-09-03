package io.github.jlmc.rikikivault.core.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class GitAuthSettingsTest {

    @Test
    void emptyHasBothFieldsUnset() {
        GitAuthSettings settings = GitAuthSettings.empty();

        assertNull(settings.sshPrivateKeyPath());
        assertNull(settings.githubToken());
    }
}
