package io.github.jlmc.rikikivault.core.adapters.keystore;

import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalKeyStoreAdapterTest {

    private static KeyPair generateX25519KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair();
    }

    private static MachineIdentity newIdentity() throws Exception {
        KeyPair keyPair = generateX25519KeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    @Test
    void existsIsFalseBeforeSaveAndTrueAfter(@TempDir Path tempDir) throws Exception {
        LocalKeyStoreAdapter adapter = new LocalKeyStoreAdapter(tempDir.resolve("identity"));

        assertFalse(adapter.exists());
        adapter.save(newIdentity());
        assertTrue(adapter.exists());
    }

    @Test
    void saveThenLoadRoundTripsTheIdentity(@TempDir Path tempDir) throws Exception {
        Path identityDir = tempDir.resolve("identity");
        MachineIdentity original = newIdentity();

        new LocalKeyStoreAdapter(identityDir).save(original);
        MachineIdentity loaded = new LocalKeyStoreAdapter(identityDir).load();

        assertEquals(original.id(), loaded.id());
        assertEquals(original.keyAlgorithm(), loaded.keyAlgorithm());
        assertArrayEquals(original.publicKey().getEncoded(), loaded.publicKey().getEncoded());
        assertArrayEquals(original.privateKey().getEncoded(), loaded.privateKey().getEncoded());
    }

    @Test
    void saveRejectsAnAlreadyExistingIdentityAndLeavesFilesUntouched(@TempDir Path tempDir) throws Exception {
        Path identityDir = tempDir.resolve("identity");
        LocalKeyStoreAdapter adapter = new LocalKeyStoreAdapter(identityDir);
        MachineIdentity first = newIdentity();
        adapter.save(first);

        byte[] privateBefore = Files.readAllBytes(identityDir.resolve("private.key"));
        byte[] publicBefore = Files.readAllBytes(identityDir.resolve("public.key"));

        assertThrows(MachineIdentityAlreadyExistsException.class, () -> adapter.save(newIdentity()));

        assertArrayEquals(privateBefore, Files.readAllBytes(identityDir.resolve("private.key")));
        assertArrayEquals(publicBefore, Files.readAllBytes(identityDir.resolve("public.key")));
    }

    @Test
    void loadThrowsWhenNoIdentityIsStored(@TempDir Path tempDir) {
        LocalKeyStoreAdapter adapter = new LocalKeyStoreAdapter(tempDir.resolve("identity"));

        assertThrows(PrivateKeyNotFoundException.class, adapter::load);
    }

    @Test
    void privateKeyFileIsOwnerOnlyOnPosixFilesystems(@TempDir Path tempDir) throws Exception {
        Path identityDir = tempDir.resolve("identity");
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported on this filesystem");

        new LocalKeyStoreAdapter(identityDir).save(newIdentity());

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(identityDir.resolve("private.key"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions);
    }
}
