package io.github.jlmc.core.adapters.encryption;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * RFC 5869 HKDF (HMAC-based Extract-and-Expand Key Derivation Function) using HMAC-SHA256.
 * The JDK's own stdlib HKDF API ({@code javax.crypto.KDF}) only arrived in JDK 24 (JEP 452);
 * this module targets JDK 17, so this is a small hand-written implementation verified against
 * the RFC's own published test vectors.
 */
final class Hkdf {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private Hkdf() {
    }

    static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) {
        byte[] prk = extract(salt, ikm);
        return expand(prk, info, length);
    }

    static byte[] extract(byte[] salt, byte[] ikm) {
        byte[] key = (salt == null || salt.length == 0) ? new byte[HASH_LENGTH] : salt;
        return hmac(key, ikm);
    }

    static byte[] expand(byte[] prk, byte[] info, int length) {
        if (length < 0 || length > 255 * HASH_LENGTH) {
            throw new IllegalArgumentException("Invalid HKDF output length: " + length);
        }
        byte[] infoBytes = info != null ? info : new byte[0];
        int blocks = (length + HASH_LENGTH - 1) / HASH_LENGTH;

        byte[] okm = new byte[length];
        byte[] previous = new byte[0];
        int copied = 0;
        for (int i = 1; i <= blocks; i++) {
            byte[] input = new byte[previous.length + infoBytes.length + 1];
            System.arraycopy(previous, 0, input, 0, previous.length);
            System.arraycopy(infoBytes, 0, input, previous.length, infoBytes.length);
            input[input.length - 1] = (byte) i;

            previous = hmac(prk, input);
            int toCopy = Math.min(HASH_LENGTH, length - copied);
            System.arraycopy(previous, 0, okm, copied, toCopy);
            copied += toCopy;
        }
        return okm;
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 must be available on any JVM", e);
        }
    }
}
