package io.github.jlmc.rikikivault.core.adapters.encryption;

import io.github.jlmc.rikikivault.core.ports.out.KeyPairGeneratorPort;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

public final class X25519KeyPairGeneratorAdapter implements KeyPairGeneratorPort {

    public static final String KEY_ALGORITHM = "X25519";

    @Override
    public KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(KEY_ALGORITHM + " must be available on any JVM (JDK 11+)", e);
        }
    }
}
