package io.github.jlmc.rikikivault.core.adapters.configuration;

/**
 * On-disk shape of config.yaml. Adapter-private (Jackson-bound) — kept separate from
 * {@link io.github.jlmc.rikikivault.core.configuration.VaultConfig} so the configuration
 * value object stays free of any serialization concern.
 */
record ConfigFileDto(String identityDirectory, EncryptionSettingsDto encryption) {
}
