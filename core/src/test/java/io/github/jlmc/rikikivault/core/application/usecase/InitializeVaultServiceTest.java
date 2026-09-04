package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.exception.VaultAlreadyInitializedException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

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

    /** No identity on this machine yet - the normal first-ever-vault case. */
    private static FakeLoadMachineIdentityUseCase noIdentityYet() {
        return new FakeLoadMachineIdentityUseCase(new PrivateKeyNotFoundException("no identity stored yet"));
    }

    @Test
    void withGitRequestedInitializesTheRepositoryAndWritesGitignoreAndEmptyManifest() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(identity);
        FakeFileStoragePort vaultRootFiles = new FakeFileStoragePort();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(
                noIdentityYet(), identityUseCase, vaultRootFiles, manifestPort, recipientRegistryPort, gitRepositoryPort);

        MachineIdentity result = service.initialize(new InitializeVaultCommand(true, "machine-a", null));

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
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(
                noIdentityYet(), identityUseCase, vaultRootFiles, manifestPort, recipientRegistryPort, gitRepositoryPort);

        service.initialize(new InitializeVaultCommand(false, "machine-a", null));

        assertEquals(0, gitRepositoryPort.initCallCount);
        assertArrayEquals("local/\n".getBytes(StandardCharsets.UTF_8), vaultRootFiles.readFile(".gitignore"));
        assertEquals(VaultManifest.empty(), manifestPort.load());
    }

    @Test
    void seedsTheRecipientRegistryWithThisMachineAsTheSoleRecipient() throws Exception {
        MachineIdentity identity = someIdentity();
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(identity);
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort();
        InitializeVaultService service = new InitializeVaultService(
                noIdentityYet(), identityUseCase, new FakeFileStoragePort(), new FakeManifestPort(),
                recipientRegistryPort, new FakeGitRepositoryPort());

        service.initialize(new InitializeVaultCommand(false, "machine-a", null));

        RecipientRegistry registry = recipientRegistryPort.load();
        assertEquals(1, registry.recipients().size());
        assertEquals(new Recipient("machine-a", identity.id(), identity.publicKey()), registry.recipients().get(0));
    }

    @Test
    void reusesAnExistingMachineIdentityInsteadOfFailing() throws Exception {
        // The machine identity is global (it represents this computer, not a single
        // vault): a second, independent vault on a machine that already used Rikiki Vault before
        // must succeed, reusing the existing identity, not be blocked because one already exists.
        MachineIdentity existingIdentity = someIdentity();
        FakeLoadMachineIdentityUseCase loadIdentityUseCase = new FakeLoadMachineIdentityUseCase(existingIdentity);
        FakeInitializeMachineIdentityUseCase initializeIdentityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort();
        InitializeVaultService service = new InitializeVaultService(
                loadIdentityUseCase, initializeIdentityUseCase, new FakeFileStoragePort(), new FakeManifestPort(),
                recipientRegistryPort, new FakeGitRepositoryPort());

        MachineIdentity result = service.initialize(new InitializeVaultCommand(false, "second-vault", null));

        assertEquals(existingIdentity, result);
        assertEquals(0, initializeIdentityUseCase.initializeCallCount);
        assertEquals(new Recipient("second-vault", existingIdentity.id(), existingIdentity.publicKey()),
                recipientRegistryPort.load().recipients().get(0));
    }

    @Test
    void withARemoteUriAndGitRequestedAddsTheRemote() throws Exception {
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(
                noIdentityYet(), identityUseCase, new FakeFileStoragePort(), new FakeManifestPort(),
                new FakeRecipientRegistryPort(), gitRepositoryPort);

        service.initialize(new InitializeVaultCommand(true, "machine-a", "git@github.com:me/my-vault.git"));

        assertEquals("git@github.com:me/my-vault.git", gitRepositoryPort.addedRemotes.get("origin"));
    }

    @Test
    void withARemoteUriButNoGitRequestedNeverAddsIt() throws Exception {
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(
                noIdentityYet(), identityUseCase, new FakeFileStoragePort(), new FakeManifestPort(),
                new FakeRecipientRegistryPort(), gitRepositoryPort);

        service.initialize(new InitializeVaultCommand(false, "machine-a", "git@github.com:me/my-vault.git"));

        assertTrue(gitRepositoryPort.addedRemotes.isEmpty());
    }

    @Test
    void withoutARemoteUriNeverAddsOne() throws Exception {
        FakeInitializeMachineIdentityUseCase identityUseCase = new FakeInitializeMachineIdentityUseCase(someIdentity());
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(
                noIdentityYet(), identityUseCase, new FakeFileStoragePort(), new FakeManifestPort(),
                new FakeRecipientRegistryPort(), gitRepositoryPort);

        service.initialize(new InitializeVaultCommand(true, "machine-a", null));

        assertTrue(gitRepositoryPort.addedRemotes.isEmpty());
    }

    @Test
    void refusesToReInitializeAVaultThatAlreadyHasRecipients() throws Exception {
        MachineIdentity existingRecipient = someIdentity();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(
                new Recipient("machine-a", existingRecipient.id(), existingRecipient.publicKey()))));
        FakeFileStoragePort vaultRootFiles = new FakeFileStoragePort();
        FakeManifestPort manifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();
        InitializeVaultService service = new InitializeVaultService(
                noIdentityYet(), new FakeInitializeMachineIdentityUseCase(someIdentity()),
                vaultRootFiles, manifestPort, recipientRegistryPort, gitRepositoryPort);

        assertThrows(VaultAlreadyInitializedException.class,
                () -> service.initialize(new InitializeVaultCommand(true, "machine-a", null)));

        assertEquals(0, gitRepositoryPort.initCallCount);
        assertTrue(vaultRootFiles.listFiles().isEmpty());
        assertEquals(VaultManifest.empty(), manifestPort.load());
    }
}
