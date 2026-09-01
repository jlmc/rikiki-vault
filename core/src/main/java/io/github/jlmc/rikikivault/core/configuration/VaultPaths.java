package io.github.jlmc.rikikivault.core.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class VaultPaths {

    private static final String VAULT_HOME_DIR_NAME = ".rikiki-vault";

    private VaultPaths() {
    }

    public static Path defaultIdentityDirectory() {
        return vaultHome().resolve("identity");
    }

    public static Path defaultConfigFile() {
        return vaultHome().resolve("config").resolve("config.yaml");
    }

    private static Path vaultHome() {
        return Paths.get(System.getProperty("user.home"), VAULT_HOME_DIR_NAME);
    }
}
