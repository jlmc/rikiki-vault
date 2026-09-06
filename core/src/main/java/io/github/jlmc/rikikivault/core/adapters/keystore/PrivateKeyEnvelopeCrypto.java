package io.github.jlmc.rikikivault.core.adapters.keystore;

import io.github.jlmc.rikikivault.core.adapters.encryption.Pbkdf2;
import io.github.jlmc.rikikivault.core.adapters.encryption.format.PrivateKeyEnvelopeCodec;
import io.github.jlmc.rikikivault.core.domain.exception.InvalidPassphraseException;
import io.github.jlmc.rikikivault.core.domain.model.PrivateKeyEnvelope;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * The actual PBKDF2 + AES-GCM crypto behind the "RVPK" private-key envelope - encode/decode of
 * the byte layout itself lives in {@link PrivateKeyEnvelopeCodec}. Split out of
 * {@link LocalKeyStoreAdapter} so it can also be reused by disaster-recovery tooling that unwraps
 * a lone {@code private.key} file outside the normal identity-directory/{@code KeyStorePort} flow
 * (see the CLI's {@code unwrap-key} command).
 */
public final class PrivateKeyEnvelopeCrypto {

    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEK_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 600_000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PrivateKeyEnvelopeCrypto() {
    }

    /**
     * @throws InvalidPassphraseException if the passphrase is wrong or the envelope is corrupted -
     *                                     an AES-GCM auth-tag failure can't tell the two apart
     */
    public static byte[] decrypt(PrivateKeyEnvelope envelope, char[] passphrase) {
        byte[] kek = null;
        try {
            kek = Pbkdf2.deriveKey(passphrase, envelope.salt(), envelope.iterations(), KEK_LENGTH_BITS);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.nonce()));
            return cipher.doFinal(envelope.ciphertext());
        } catch (GeneralSecurityException e) {
            throw new InvalidPassphraseException(
                    "Failed to unlock the private key: wrong passphrase or the key file is corrupted", e);
        } finally {
            wipe(kek);
        }
    }

    public static PrivateKeyEnvelope encrypt(byte[] pkcs8Bytes, char[] passphrase) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        SECURE_RANDOM.nextBytes(nonce);

        byte[] kek = null;
        try {
            kek = Pbkdf2.deriveKey(passphrase, salt, PBKDF2_ITERATIONS, KEK_LENGTH_BITS);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(pkcs8Bytes);
            return new PrivateKeyEnvelope(PrivateKeyEnvelopeCodec.KDF_PBKDF2_HMAC_SHA256, PBKDF2_ITERATIONS, salt, nonce, ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt the private key", e);
        } finally {
            wipe(kek);
        }
    }

    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
