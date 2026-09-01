package io.github.jlmc.core.adapters.encryption;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

/**
 * Thin wrapper around the JDK's stdlib HKDF-SHA256 ({@code javax.crypto.KDF}, JEP 452,
 * stable since JDK 24). This module's minimum Java version is 25, so no hand-written
 * HKDF math is needed here — verified against the RFC 5869 test vectors in HkdfTest.
 */
final class Hkdf {

    private static final String ALGORITHM = "HKDF-SHA256";

    private Hkdf() {
    }

    static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            KDF kdf = KDF.getInstance(ALGORITHM);
            HKDFParameterSpec spec = builderWithIkmAndSalt(ikm, salt)
                    .thenExpand(info != null ? info : new byte[0], length);
            return kdf.deriveData(spec);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(ALGORITHM + " must be available on any JVM (JDK 24+)", e);
        }
    }

    static byte[] extract(byte[] salt, byte[] ikm) {
        try {
            KDF kdf = KDF.getInstance(ALGORITHM);
            HKDFParameterSpec.Extract spec = builderWithIkmAndSalt(ikm, salt).extractOnly();
            return kdf.deriveData(spec);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(ALGORITHM + " must be available on any JVM (JDK 24+)", e);
        }
    }

    private static HKDFParameterSpec.Builder builderWithIkmAndSalt(byte[] ikm, byte[] salt) {
        HKDFParameterSpec.Builder builder = HKDFParameterSpec.ofExtract().addIKM(ikm);
        // RFC 5869: an absent salt defaults to a zero-filled HashLen string; the stdlib
        // applies that same default when addSalt is never called, so an explicitly
        // empty salt is treated identically to "no salt provided".
        if (salt != null && salt.length > 0) {
            builder = builder.addSalt(salt);
        }
        return builder;
    }
}
