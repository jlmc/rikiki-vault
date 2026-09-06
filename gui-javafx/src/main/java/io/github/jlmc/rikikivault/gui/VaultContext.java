package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalGitAuthSettingsAdapter;
import io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.git.JGitRepositoryAdapter;
import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.adapters.manifest.JsonManifestFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.recipients.JsonRecipientRegistryFileAdapter;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bundles the adapters for one open vault directory - same wiring {@code cli/Main.java} already
 * proved, just held as a reusable object instead of rebuilt per command, since the GUI keeps a
 * vault open across many actions.
 */
public record VaultContext(
        Path vaultRoot,
        LocalFileSystemAdapter localFiles,
        LocalFileSystemAdapter documentsFiles,
        JsonManifestFileAdapter manifestPort,
        JsonRecipientRegistryFileAdapter recipientRegistryPort,
        JGitRepositoryAdapter gitRepositoryPort,
        KeyStorePort keyStorePort,
        JceHybridEncryptionAdapter encryptionPort,
        Sha256HashAdapter hashPort) {

    public static VaultContext at(Path vaultRoot) {
        VaultConfig config = new YamlConfigFileAdapter(VaultPaths.defaultConfigFile()).load();
        return new VaultContext(
                vaultRoot,
                new LocalFileSystemAdapter(vaultRoot.resolve("local")),
                new LocalFileSystemAdapter(vaultRoot.resolve("documents")),
                new JsonManifestFileAdapter(vaultRoot.resolve("vault").resolve("manifest.json")),
                new JsonRecipientRegistryFileAdapter(vaultRoot.resolve("vault").resolve("recipients.json")),
                new JGitRepositoryAdapter(vaultRoot, new LocalGitAuthSettingsAdapter(VaultPaths.defaultPreferencesDirectory())),
                new LocalKeyStoreAdapter(config.identityDirectory()),
                new JceHybridEncryptionAdapter(config.encryptionSettings()),
                new Sha256HashAdapter());
    }

    public boolean isInitialized() {
        return Files.exists(vaultRoot.resolve("vault").resolve("manifest.json"));
    }

    public VaultContext withKeyStorePort(KeyStorePort newKeyStorePort) {
        return new VaultContext(vaultRoot, localFiles, documentsFiles, manifestPort, recipientRegistryPort,
                gitRepositoryPort, newKeyStorePort, encryptionPort, hashPort);
    }
}
