package io.github.jlmc.rikikivault.core.adapters.keystore;

import io.github.jlmc.rikikivault.core.adapters.encryption.Pbkdf2;
import io.github.jlmc.rikikivault.core.adapters.encryption.format.PrivateKeyEnvelopeCodec;
import io.github.jlmc.rikikivault.core.domain.exception.InvalidPassphraseException;
import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.exception.PassphraseRequiredException;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PrivateKeyEnvelope;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Stores the machine identity as PKCS8/X.509 DER files under a directory, restricted to the
 * owner via POSIX permissions where the filesystem supports them (best-effort fallback
 * otherwise). Never overwrites an existing identity via {@link #save(MachineIdentity)} and never
 * leaks key material in exceptions.
 *
 * <p>The private key file is optionally passphrase-protected: an "RVPK" envelope (PBKDF2-derived
 * key-encryption key, AES-GCM) wraps the PKCS8 bytes instead of storing them raw. Detection is
 * self-describing (a magic-byte prefix), so a legacy unprotected key keeps loading exactly as
 * before - protection is strictly opt-in via {@link #changePassphrase(char[], char[])}.
 *
 * <p>{@link #changePassphrase} contract:
 * <table>
 * <caption>changePassphrase(currentOrNull, newOrNull)</caption>
 * <tr><th>current state</th><th>currentOrNull</th><th>newOrNull</th><th>result</th></tr>
 * <tr><td>unprotected</td><td>null</td><td>new</td><td>protects with {@code new}</td></tr>
 * <tr><td>unprotected</td><td>null</td><td>null</td><td>{@link IllegalArgumentException}</td></tr>
 * <tr><td>unprotected</td><td>non-null</td><td>any</td><td>{@link IllegalArgumentException}</td></tr>
 * <tr><td>protected</td><td>correct</td><td>new</td><td>rotates to {@code new}</td></tr>
 * <tr><td>protected</td><td>correct</td><td>null</td><td>removes protection</td></tr>
 * <tr><td>protected</td><td>wrong</td><td>any</td><td>{@link InvalidPassphraseException}</td></tr>
 * <tr><td>protected</td><td>null</td><td>any</td><td>{@link PassphraseRequiredException}</td></tr>
 * </table>
 */
public final class LocalKeyStoreAdapter implements KeyStorePort {

    private static final String PRIVATE_KEY_FILE = "private.key";
    private static final String PUBLIC_KEY_FILE = "public.key";
    private static final String KEY_ALGORITHM = "X25519";

    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEK_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 600_000;

    private final Path identityDirectory;
    private final PrivateKeyEnvelopeCodec envelopeCodec = new PrivateKeyEnvelopeCodec();
    private final SecureRandom secureRandom = new SecureRandom();

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
        byte[] privateKeyFileBytes = readPrivateKeyFile();
        if (envelopeCodec.isEnvelope(privateKeyFileBytes)) {
            throw new PassphraseRequiredException(
                    "Machine identity at " + identityDirectory + " is passphrase-protected");
        }
        return buildIdentity(privateKeyFileBytes);
    }

    @Override
    public MachineIdentity load(char[] passphrase) {
        byte[] privateKeyFileBytes = readPrivateKeyFile();
        if (!envelopeCodec.isEnvelope(privateKeyFileBytes)) {
            return buildIdentity(privateKeyFileBytes);
        }
        byte[] pkcs8Bytes = decryptEnvelope(envelopeCodec.decode(privateKeyFileBytes), passphrase);
        try {
            return buildIdentity(pkcs8Bytes);
        } finally {
            wipe(pkcs8Bytes);
        }
    }

    @Override
    public boolean isPassphraseProtected() {
        return exists() && envelopeCodec.isEnvelope(readPrivateKeyFile());
    }

    @Override
    public void changePassphrase(char[] currentOrNull, char[] newOrNull) {
        if (!exists()) {
            throw new PrivateKeyNotFoundException("No machine identity found at " + identityDirectory);
        }
        if (currentOrNull == null && newOrNull == null) {
            throw new IllegalArgumentException("At least one of currentOrNull/newOrNull must be non-null");
        }

        byte[] currentPrivateKeyFileBytes = readPrivateKeyFile();
        boolean currentlyProtected = envelopeCodec.isEnvelope(currentPrivateKeyFileBytes);

        if (!currentlyProtected && currentOrNull != null) {
            throw new IllegalArgumentException("Identity is not passphrase-protected; there is nothing to verify currentOrNull against");
        }

        byte[] pkcs8Bytes = null;
        try {
            if (currentlyProtected) {
                if (currentOrNull == null) {
                    throw new PassphraseRequiredException(
                            "Machine identity at " + identityDirectory + " is passphrase-protected");
                }
                pkcs8Bytes = decryptEnvelope(envelopeCodec.decode(currentPrivateKeyFileBytes), currentOrNull);
            } else {
                pkcs8Bytes = currentPrivateKeyFileBytes;
            }

            byte[] newContent = newOrNull != null ? encryptEnvelope(pkcs8Bytes, newOrNull) : pkcs8Bytes;
            writeFileSecurelyReplacing(privateKeyPath(), newContent);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist machine identity under " + identityDirectory, e);
        } finally {
            wipe(pkcs8Bytes);
        }
    }

    private byte[] readPrivateKeyFile() {
        if (!exists()) {
            throw new PrivateKeyNotFoundException("No machine identity found at " + identityDirectory);
        }
        try {
            return Files.readAllBytes(privateKeyPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read machine identity from " + identityDirectory, e);
        }
    }

    private MachineIdentity buildIdentity(byte[] pkcs8Bytes) {
        try {
            byte[] publicKeyBytes = Files.readAllBytes(publicKeyPath());

            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            return new MachineIdentity(KeyFingerprint.of(publicKey), publicKey, privateKey, KEY_ALGORITHM);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read machine identity from " + identityDirectory, e);
        } catch (GeneralSecurityException e) {
            throw new PrivateKeyNotFoundException("Stored machine identity at " + identityDirectory + " is unreadable or corrupted", e);
        }
    }

    private byte[] decryptEnvelope(PrivateKeyEnvelope envelope, char[] passphrase) {
        byte[] kek = null;
        try {
            kek = Pbkdf2.deriveKey(passphrase, envelope.salt(), envelope.iterations(), KEK_LENGTH_BITS);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.nonce()));
            return cipher.doFinal(envelope.ciphertext());
        } catch (GeneralSecurityException e) {
            // AEADBadTagException (auth tag mismatch) is by far the common case - wrong passphrase -
            // but any GeneralSecurityException here is treated the same way, deliberately without
            // distinguishing "wrong passphrase" from "corrupted file": the two are indistinguishable
            // from the cipher's perspective, and claiming one over the other would be a guess.
            throw new InvalidPassphraseException(
                    "Failed to unlock machine identity: wrong passphrase or the key file is corrupted", e);
        } finally {
            wipe(kek);
        }
    }

    private byte[] encryptEnvelope(byte[] pkcs8Bytes, char[] passphrase) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        secureRandom.nextBytes(nonce);

        byte[] kek = null;
        try {
            kek = Pbkdf2.deriveKey(passphrase, salt, PBKDF2_ITERATIONS, KEK_LENGTH_BITS);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(pkcs8Bytes);

            PrivateKeyEnvelope envelope = new PrivateKeyEnvelope(
                    PrivateKeyEnvelopeCodec.KDF_PBKDF2_HMAC_SHA256, PBKDF2_ITERATIONS, salt, nonce, ciphertext);
            return envelopeCodec.encode(envelope);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt machine identity", e);
        } finally {
            wipe(kek);
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

    /**
     * Crash-safe overwrite for an existing file: writes to a sibling temp file (same restrictive
     * permissions from creation, same directory so the final move can be atomic), reads it back to
     * confirm the write landed intact, then atomically replaces the target. A crash or I/O failure
     * at any point before the final move leaves the original file untouched.
     */
    private static void writeFileSecurelyReplacing(Path file, byte[] content) throws IOException {
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.deleteIfExists(tempFile);
        try {
            writeFileSecurely(tempFile, content);
            byte[] writtenBack = Files.readAllBytes(tempFile);
            if (!Arrays.equals(content, writtenBack)) {
                throw new IOException("Verification read-back did not match what was written to " + tempFile);
            }
            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
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

    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
