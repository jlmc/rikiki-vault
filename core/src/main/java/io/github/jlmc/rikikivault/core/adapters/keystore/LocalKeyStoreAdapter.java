package io.github.jlmc.rikikivault.core.adapters.keystore;

import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Stores the machine identity as PKCS8/X.509 DER files under a directory, restricted to the
 * owner via POSIX permissions where the filesystem supports them (best-effort fallback
 * otherwise). Never overwrites an existing identity and never leaks key material in exceptions.
 */
public final class LocalKeyStoreAdapter implements KeyStorePort {

    private static final String PRIVATE_KEY_FILE = "private.key";
    private static final String PUBLIC_KEY_FILE = "public.key";
    private static final String KEY_ALGORITHM = "X25519";

    private final Path identityDirectory;

    public LocalKeyStoreAdapter(Path identityDirectory) {
        this.identityDirectory = Objects.requireNonNull(identityDirectory, "identityDirectory must not be null");
    }

    @Override
    public boolean exists() {
        return Files.exists(privateKeyPath()) && Files.exists(publicKeyPath());
    }

    @Override
    public void save(MachineIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (exists()) {
            throw new MachineIdentityAlreadyExistsException(
                    "A machine identity already exists at " + identityDirectory + "; refusing to overwrite it");
        }
        try {
            createDirectorySecurely(identityDirectory);
            writeFileSecurely(privateKeyPath(), identity.privateKey().getEncoded());
            writeFileSecurely(publicKeyPath(), identity.publicKey().getEncoded());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist machine identity under " + identityDirectory, e);
        }
    }

    @Override
    public MachineIdentity load() {
        if (!exists()) {
            throw new PrivateKeyNotFoundException("No machine identity found at " + identityDirectory);
        }
        try {
            byte[] privateKeyBytes = Files.readAllBytes(privateKeyPath());
            byte[] publicKeyBytes = Files.readAllBytes(publicKeyPath());

            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            return new MachineIdentity(KeyFingerprint.of(publicKey), publicKey, privateKey, KEY_ALGORITHM);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read machine identity from " + identityDirectory, e);
        } catch (GeneralSecurityException e) {
            throw new PrivateKeyNotFoundException("Stored machine identity at " + identityDirectory + " is unreadable or corrupted", e);
        }
    }

    private Path privateKeyPath() {
        return identityDirectory.resolve(PRIVATE_KEY_FILE);
    }

    private Path publicKeyPath() {
        return identityDirectory.resolve(PUBLIC_KEY_FILE);
    }

    private static void createDirectorySecurely(Path directory) throws IOException {
        if (Files.exists(directory)) {
            return;
        }
        if (supportsPosixPermissions(directory)) {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(ownerOnly));
        } else {
            Files.createDirectories(directory);
            restrictToOwnerBestEffort(directory);
        }
    }

    private static void writeFileSecurely(Path file, byte[] content) throws IOException {
        if (supportsPosixPermissions(file.getParent())) {
            Set<PosixFilePermission> ownerReadWrite = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(ownerReadWrite));
            Files.write(file, content);
        } else {
            Files.write(file, content);
            restrictToOwnerBestEffort(file);
        }
    }

    private static void restrictToOwnerBestEffort(Path path) {
        // Non-POSIX filesystem (e.g. Windows): no atomic "create with permissions" available.
        // This is a best-effort fallback, not a full ACL solution.
        java.io.File file = path.toFile();
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
    }

    private static boolean supportsPosixPermissions(Path path) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }
}
