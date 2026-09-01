package io.github.jlmc.core.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionSettingsTest {

    @Test
    void defaultsUse256BitKeys() {
        assertEquals(256, EncryptionSettings.defaults().aesKeyBits());
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 192, 256})
    void acceptsSupportedAesKeySizes(int bits) {
        assertEquals(bits, new EncryptionSettings(bits).aesKeyBits());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 64, 512, 255})
    void rejectsUnsupportedAesKeySizes(int bits) {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionSettings(bits));
    }
}
