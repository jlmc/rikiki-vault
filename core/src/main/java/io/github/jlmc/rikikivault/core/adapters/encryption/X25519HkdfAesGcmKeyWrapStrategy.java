package io.github.jlmc.rikikivault.core.adapters.encryption;

import io.github.jlmc.rikikivault.core.domain.exception.DecryptionException;
import io.github.jlmc.rikikivault.core.domain.exception.EncryptionException;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * ECIES-style key wrap: a fresh ephemeral X25519 key pair per call, ECDH with the
 * recipient's public key, HKDF-SHA256 to derive a key-encryption-key bound to the
 * ephemeral public key, then AES-256-GCM to actually wrap the file key.
 *
 * <p>The HKDF info is bound to the ephemeral public key (not the recipient's), because
 * {@link #unwrap(byte[], PrivateKey)} only ever receives a bare {@link PrivateKey}
 * (per the exact {@code EncryptionPort} signature) with no way to recover the
 * recipient's own public key bytes from it — so only data present in the wrapped
 * blob itself (recoverable identically on both sides) can feed the derivation.
 */
final class X25519HkdfAesGcmKeyWrapStrategy implements KeyWrapStrategy {

    private static final String X25519 = "X25519";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int WRAP_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEK_LENGTH_BYTES = 32;
    private static final byte[] HKDF_INFO_LABEL = "RIKIKI-VAULT-V1-FILE-KEY-WRAP".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public byte[] wrap(byte[] keyToWrap, PublicKey recipientPublicKey) {
        byte[] sharedSecret = null;
        byte[] kek = null;
        try {
            KeyPairGenerator ephemeralGenerator = KeyPairGenerator.getInstance(X25519);
            ephemeralGenerator.initialize(NamedParameterSpec.X25519, secureRandom);
            KeyPair ephemeralKeyPair = ephemeralGenerator.generateKeyPair();
            byte[] ephemeralPublicKeyEncoded = ephemeralKeyPair.getPublic().getEncoded();

            sharedSecret = agree(ephemeralKeyPair.getPrivate(), recipientPublicKey);
            kek = Hkdf.derive(sharedSecret, null, hkdfInfo(ephemeralPublicKeyEncoded), KEK_LENGTH_BYTES);

            byte[] wrapNonce = new byte[WRAP_NONCE_LENGTH];
            secureRandom.nextBytes(wrapNonce);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, wrapNonce));
            byte[] wrappedKey = cipher.doFinal(keyToWrap);

            return encodeBlob(ephemeralPublicKeyEncoded, wrapNonce, wrappedKey);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to wrap file key for recipient", e);
        } finally {
            wipe(sharedSecret);
            wipe(kek);
        }
    }

    @Override
    public byte[] unwrap(byte[] wrappedKeyBlob, PrivateKey recipientPrivateKey) {
        byte[] sharedSecret = null;
        byte[] kek = null;
        try {
            DecodedBlob decoded = decodeBlob(wrappedKeyBlob);
            PublicKey ephemeralPublicKey = KeyFactory.getInstance(X25519)
                    .generatePublic(new X509EncodedKeySpec(decoded.ephemeralPublicKeyEncoded()));

            sharedSecret = agree(recipientPrivateKey, ephemeralPublicKey);
            kek = Hkdf.derive(sharedSecret, null, hkdfInfo(decoded.ephemeralPublicKeyEncoded()), KEK_LENGTH_BYTES);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, decoded.wrapNonce()));
            return cipher.doFinal(decoded.wrappedKey());
        } catch (GeneralSecurityException e) {
            throw new DecryptionException("Failed to unwrap file key", e);
        } finally {
            wipe(sharedSecret);
            wipe(kek);
        }
    }

    private static byte[] agree(PrivateKey privateKey, PublicKey publicKey) throws GeneralSecurityException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance(X25519);
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(publicKey, true);
        return keyAgreement.generateSecret();
    }

    private static byte[] hkdfInfo(byte[] ephemeralPublicKeyEncoded) {
        byte[] info = new byte[HKDF_INFO_LABEL.length + ephemeralPublicKeyEncoded.length];
        System.arraycopy(HKDF_INFO_LABEL, 0, info, 0, HKDF_INFO_LABEL.length);
        System.arraycopy(ephemeralPublicKeyEncoded, 0, info, HKDF_INFO_LABEL.length, ephemeralPublicKeyEncoded.length);
        return info;
    }

    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    private record DecodedBlob(byte[] ephemeralPublicKeyEncoded, byte[] wrapNonce, byte[] wrappedKey) {
    }

    private static byte[] encodeBlob(byte[] ephemeralPublicKeyEncoded, byte[] wrapNonce, byte[] wrappedKey) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeShort(ephemeralPublicKeyEncoded.length);
            out.write(ephemeralPublicKeyEncoded);
            out.write(wrapNonce);
            out.write(wrappedKey);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    private static DecodedBlob decodeBlob(byte[] blob) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            int ephemeralKeyLength = in.readUnsignedShort();
            byte[] ephemeralPublicKeyEncoded = new byte[ephemeralKeyLength];
            in.readFully(ephemeralPublicKeyEncoded);

            byte[] wrapNonce = new byte[WRAP_NONCE_LENGTH];
            in.readFully(wrapNonce);

            byte[] wrappedKey = in.readAllBytes();
            return new DecodedBlob(ephemeralPublicKeyEncoded, wrapNonce, wrappedKey);
        } catch (IOException e) {
            throw new DecryptionException("Malformed wrapped key blob", e);
        }
    }
}
