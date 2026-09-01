package io.github.jlmc.core.ports.out;

import io.github.jlmc.core.domain.model.EncryptedFile;
import io.github.jlmc.core.domain.model.PlaintextFile;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;

public interface EncryptionPort {

    EncryptedFile encrypt(PlaintextFile file, Collection<PublicKey> recipients);

    PlaintextFile decrypt(EncryptedFile file, PrivateKey privateKey);
}
