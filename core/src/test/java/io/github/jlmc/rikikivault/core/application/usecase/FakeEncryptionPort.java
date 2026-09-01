package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.RecipientKeyEntry;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class FakeEncryptionPort implements EncryptionPort {

    int encryptCallCount = 0;
    int decryptCallCount = 0;
    PlaintextFile fileToReturnOnDecrypt;

    @Override
    public EncryptedFile encrypt(PlaintextFile file, Collection<PublicKey> recipients) {
        encryptCallCount++;
        List<RecipientKeyEntry> entries = recipients.stream()
                .map(publicKey -> new RecipientKeyEntry(KeyFingerprint.of(publicKey), new byte[]{1}))
                .collect(Collectors.toList());
        return new EncryptedFile("RV01", 1, 1, file.fileName(), entries, new byte[12], new byte[]{1, 2, 3});
    }

    @Override
    public PlaintextFile decrypt(EncryptedFile file, PrivateKey privateKey) {
        decryptCallCount++;
        return fileToReturnOnDecrypt;
    }
}
