package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitializeVaultServiceTest {

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    @Test
    void withGitRequestedInitializesTheRepositoryAndWritesGitignoreAndEmptyManifest() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(identity);
        FakeFileStoragePort vaultRootFiles = new FakeFileStoragePort();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(identityUseCase, vaultRootFiles, manifestPort, gitRepositoryPort);

        MachineIdentity result = service.initialize(new InitializeVaultCommand(true));

        assertEquals(identity, result);
        assertEquals(1, gitRepositoryPort.initCallCount);
        assertArrayEquals("local/\n".getBytes(StandardCharsets.UTF_8), vaultRootFiles.readFile(".gitignore"));
        assertEquals(VaultManifest.empty(), manifestPort.load());
    }

    @Test
    void withoutGitRequestedSkipsRepositoryInitButStillWritesGitignoreAndManifest() throws Exception {
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        FakeFileStoragePort vaultRootFiles = new FakeFileStoragePort();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(identityUseCase, vaultRootFiles, manifestPort, gitRepositoryPort);

        service.initialize(new InitializeVaultCommand(false));

        assertEquals(0, gitRepositoryPort.initCallCount);
        assertArrayEquals("local/\n".getBytes(StandardCharsets.UTF_8), vaultRootFiles.readFile(".gitignore"));
        assertEquals(VaultManifest.empty(), manifestPort.load());
    }

    @Test
    void propagatesAndShortCircuitsWhenTheIdentityAlreadyExists() {
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(
                new MachineIdentityAlreadyExistsException("already exists"));
        FakeFileStoragePort vaultRootFiles = new FakeFileStoragePort();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(identityUseCase, vaultRootFiles, manifestPort, gitRepositoryPort);

        assertThrows(MachineIdentityAlreadyExistsException.class,
                () -> service.initialize(new InitializeVaultCommand(true)));

        assertEquals(0, gitRepositoryPort.initCallCount);
        assertTrue(vaultRootFiles.listFiles().isEmpty());
        assertEquals(VaultManifest.empty(), manifestPort.load());
    }
}
