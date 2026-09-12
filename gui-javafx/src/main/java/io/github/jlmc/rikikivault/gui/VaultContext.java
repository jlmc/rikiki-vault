package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalGitAuthSettingsAdapter;
import io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.git.JGitRepositoryAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.adapters.manifest.EncryptedManifestFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.recipients.JsonRecipientRegistryFileAdapter;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bundles the adapters for one open vault directory - same wiring {@code cli/Main.java} already
 * proved, just held as a reusable object instead of rebuilt per command, since the GUI keeps a
 * vault open across many actions.
 *
 * <p>{@link #manifestPort()} is a computed accessor, not a stored component, because the
 * {@link EncryptedManifestFileAdapter} it builds must always resolve the *current*
 * {@link #keyStorePort()} (which {@link #withKeyStorePort} swaps out after a passphrase is
 * unlocked or cached) and the *current* {@link #recipientRegistryPort()} contents (which change on
 * every authorize/revoke) - baking either into a long-lived field would risk decrypting/encrypting
 * against stale state.
 */
public record VaultContext(
        Path vaultRoot,
        LocalFileSystemAdapter localFiles,
        LocalFileSystemAdapter documentsFiles,
        JsonRecipientRegistryFileAdapter recipientRegistryPort,
        JGitRepositoryAdapter gitRepositoryPort,
        KeyStorePort keyStorePort,
        JceHybridEncryptionAdapter encryptionPort) {

    public static VaultContext at(Path vaultRoot) {
        VaultConfig config = new YamlConfigFileAdapter(VaultPaths.defaultConfigFile()).load();
        return new VaultContext(
                vaultRoot,
                new LocalFileSystemAdapter(vaultRoot.resolve("local")),
                new LocalFileSystemAdapter(vaultRoot.resolve("documents")),
                new JsonRecipientRegistryFileAdapter(vaultRoot.resolve("vault").resolve("recipients.json")),
                new JGitRepositoryAdapter(vaultRoot, new LocalGitAuthSettingsAdapter(VaultPaths.defaultPreferencesDirectory())),
                new LocalKeyStoreAdapter(config.identityDirectory()),
                new JceHybridEncryptionAdapter(config.encryptionSettings()));
    }

    public ManifestPort manifestPort() {
        return new EncryptedManifestFileAdapter(
                vaultRoot.resolve("vault").resolve("manifest.json"),
                encryptionPort,
                () -> keyStorePort.load().privateKey(),
                () -> recipientRegistryPort.load().recipients().stream().map(Recipient::publicKey).toList());
    }

    public boolean isInitialized() {
        return Files.exists(vaultRoot.resolve("vault").resolve("manifest.json"));
    }

    public VaultContext withKeyStorePort(KeyStorePort newKeyStorePort) {
        return new VaultContext(vaultRoot, localFiles, documentsFiles, recipientRegistryPort,
                gitRepositoryPort, newKeyStorePort, encryptionPort);
    }
}
