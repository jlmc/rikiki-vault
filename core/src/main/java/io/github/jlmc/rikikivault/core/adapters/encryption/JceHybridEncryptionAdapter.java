package io.github.jlmc.rikikivault.core.adapters.encryption;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.domain.exception.DecryptionException;
import io.github.jlmc.rikikivault.core.domain.exception.EncryptionException;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.RecipientKeyEntry;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Hybrid encryption: content is sealed with AES-GCM under a fresh random per-file key,
 * and that key is wrapped once per recipient via {@link KeyWrapStrategy}. The application
 * layer only ever sees {@link EncryptionPort} — this class is the only place that knows
 * both "AES-GCM" and "X25519+HKDF" are the concrete choices behind it.
 */
public final class JceHybridEncryptionAdapter implements EncryptionPort {

    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int CONTENT_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final EncryptionSettings settings;
    private final KeyWrapStrategy keyWrapStrategy;
    private final SecureRandom secureRandom = new SecureRandom();

    public JceHybridEncryptionAdapter(EncryptionSettings settings) {
        this(settings, new X25519HkdfAesGcmKeyWrapStrategy());
    }

    JceHybridEncryptionAdapter(EncryptionSettings settings, KeyWrapStrategy keyWrapStrategy) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.keyWrapStrategy = Objects.requireNonNull(keyWrapStrategy, "keyWrapStrategy must not be null");
    }

    @Override
    public EncryptedFile encrypt(PlaintextFile file, Collection<PublicKey> recipients) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(recipients, "recipients must not be null");
        if (recipients.isEmpty()) {
            throw new EncryptionException("At least one recipient is required to encrypt a file");
        }

        byte[] contentKey = null;
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(settings.aesKeyBits(), secureRandom);
            contentKey = keyGenerator.generateKey().getEncoded();

            byte[] contentNonce = new byte[CONTENT_NONCE_LENGTH];
            secureRandom.nextBytes(contentNonce);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, contentNonce));
            byte[] sealedContent = cipher.doFinal(file.content());

            List<RecipientKeyEntry> recipientEntries = new ArrayList<>(recipients.size());
            for (PublicKey recipient : recipients) {
                byte[] wrappedKey = keyWrapStrategy.wrap(contentKey, recipient);
                recipientEntries.add(new RecipientKeyEntry(KeyFingerprint.of(recipient), wrappedKey));
            }

            return new EncryptedFile(
                    RvEncryptedFileFormatCodec.FORMAT_VERSION,
                    RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                    RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                    file.fileName(),
                    recipientEntries,
                    contentNonce,
                    sealedContent);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to encrypt file", e);
        } finally {
            wipe(contentKey);
        }
    }

    @Override
    public PlaintextFile decrypt(EncryptedFile file, PrivateKey privateKey) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");

        byte[] contentKey = null;
        try {
            contentKey = unwrapContentKey(file, privateKey);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, file.contentNonce()));
            byte[] plaintext = cipher.doFinal(file.sealedContent());

            return new PlaintextFile(file.originalFileName(), plaintext);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new DecryptionException("Failed to decrypt file: authentication failed or data is corrupted", e);
        } finally {
            wipe(contentKey);
        }
    }

    private byte[] unwrapContentKey(EncryptedFile file, PrivateKey privateKey) {
        for (RecipientKeyEntry entry : file.recipientEntries()) {
            try {
                return keyWrapStrategy.unwrap(entry.wrappedKeyBlob(), privateKey);
            } catch (DecryptionException wrongKeyOrCorruptedEntry) {
                // Try the next recipient entry; a genuine "no entry matches" is reported below.
            }
        }
        throw new UnauthorizedMachineException("No recipient entry could be unwrapped with the given private key");
    }

    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
