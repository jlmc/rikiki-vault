package io.github.jlmc.rikikivault.core.e2e;

import io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.EncryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.EncryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.out.ConfigPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Composes only real adapters through the real application services — no fakes, no CLI, no Git —
 * to validate the full vertical slice this milestone delivers: config -> identity -> encrypt
 * -> decrypt, byte-for-byte.
 */
class EndToEndIdentityAndEncryptionTest {

    @Test
    void configDrivesIdentityCreationAndAFullEncryptDecryptRoundTrip(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("config").resolve("config.yaml");
        ConfigPort configPort = new YamlConfigFileAdapter(configFile);

        VaultConfig configToSave = new VaultConfig(tempDir.resolve("identity"), EncryptionSettings.defaults());
        configPort.save(configToSave);
        VaultConfig config = configPort.load();
        assertEquals(configToSave, config);

        // Initialize the identity once...
        new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), new LocalKeyStoreAdapter(config.identityDirectory()))
                .initialize();

        // ...then reload it through a fresh adapter instance, the way a later command invocation would.
        MachineIdentity identity = new LoadMachineIdentityService(new LocalKeyStoreAdapter(config.identityDirectory())).load();

        JceHybridEncryptionAdapter encryptionAdapter = new JceHybridEncryptionAdapter(config.encryptionSettings());
        byte[] content = new byte[4096];
        new SecureRandom().nextBytes(content);
        PlaintextFile original = new PlaintextFile("cv.pdf", content);

        EncryptedFile encrypted = new EncryptFileService(encryptionAdapter)
                .encrypt(new EncryptFileCommand(original, List.of(identity.publicKey())));
        PlaintextFile decrypted = new DecryptFileService(encryptionAdapter)
                .decrypt(new DecryptFileCommand(encrypted, identity));

        assertEquals(original, decrypted);
        assertEquals("cv.pdf", encrypted.originalFileName());
    }
}
