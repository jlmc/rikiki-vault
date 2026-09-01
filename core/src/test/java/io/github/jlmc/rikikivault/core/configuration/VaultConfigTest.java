package io.github.jlmc.rikikivault.core.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultConfigTest {

    @Test
    void defaultsUseDefaultIdentityDirectoryAndEncryptionSettings() {
        VaultConfig config = VaultConfig.defaults();

        assertEquals(VaultPaths.defaultIdentityDirectory(), config.identityDirectory());
        assertEquals(EncryptionSettings.defaults(), config.encryptionSettings());
    }

    @Test
    void rejectsNullIdentityDirectory() {
        assertThrows(NullPointerException.class,
                () -> new VaultConfig(null, EncryptionSettings.defaults()));
    }

    @Test
    void rejectsNullEncryptionSettings() {
        assertThrows(NullPointerException.class,
                () -> new VaultConfig(Path.of("/tmp/identity"), null));
    }
}
