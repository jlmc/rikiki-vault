package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.ports.out.KeyPairGeneratorPort;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

final class FakeKeyPairGeneratorPort implements KeyPairGeneratorPort {

    int generateCallCount = 0;

    @Override
    public KeyPair generate() {
        generateCallCount++;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
            generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
