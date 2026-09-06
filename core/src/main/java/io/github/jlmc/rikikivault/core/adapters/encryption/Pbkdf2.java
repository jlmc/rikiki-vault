package io.github.jlmc.rikikivault.core.adapters.encryption;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;

/**
 * Thin wrapper around the JDK's stdlib PBKDF2-HMAC-SHA256 ({@code javax.crypto.SecretKeyFactory}),
 * used to stretch a user passphrase into a key-encryption key. Unlike {@link Hkdf} (which derives
 * from already-high-entropy secret material), this is deliberately slow - that's the point of a
 * password-based KDF - so callers must never invoke it on the JavaFX Application Thread.
 */
public final class Pbkdf2 {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private Pbkdf2() {
    }

    public static byte[] deriveKey(char[] passphrase, byte[] salt, int iterations, int keyLengthBits) {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, keyLengthBits);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " must be available on any JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
