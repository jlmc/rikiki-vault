package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.RecipientKeyEntry;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class FakeEncryptionPort implements EncryptionPort {

    int encryptCallCount = 0;
    int decryptCallCount = 0;
    PlaintextFile fileToReturnOnDecrypt;
    final List<Collection<PublicKey>> receivedRecipients = new ArrayList<>();

    @Override
    public EncryptedFile encrypt(PlaintextFile file, Collection<PublicKey> recipients) {
        encryptCallCount++;
        receivedRecipients.add(recipients);
        List<RecipientKeyEntry> entries = recipients.stream()
                .map(publicKey -> new RecipientKeyEntry(KeyFingerprint.of(publicKey), new byte[]{1}))
                .collect(Collectors.toList());
        // Real EncryptedFile no longer carries a filename (RV02) - formatVersion is repurposed
        // here, test-only, to let FakeDecryptFileUseCase look up a canned result by name.
        return new EncryptedFile(file.fileName(), 1, 1, entries, new byte[12], new byte[]{1, 2, 3});
    }

    @Override
    public PlaintextFile decrypt(EncryptedFile file, PrivateKey privateKey) {
        decryptCallCount++;
        return fileToReturnOnDecrypt;
    }
}
