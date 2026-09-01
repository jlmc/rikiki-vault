package io.github.jlmc.rikikivault.core.ports.out;

import java.security.KeyPair;

public interface KeyPairGeneratorPort {

    KeyPair generate();
}
