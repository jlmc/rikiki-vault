package io.github.jlmc.core.configuration;

import java.nio.file.Path;
import java.util.Objects;

public record VaultConfig(Path identityDirectory, EncryptionSettings encryptionSettings) {

    public VaultConfig {
        Objects.requireNonNull(identityDirectory, "identityDirectory must not be null");
        Objects.requireNonNull(encryptionSettings, "encryptionSettings must not be null");
    }

    public static VaultConfig defaults() {
        return new VaultConfig(VaultPaths.defaultIdentityDirectory(), EncryptionSettings.defaults());
    }
}
