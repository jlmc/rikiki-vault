package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.DecryptionException;
import io.github.jlmc.rikikivault.core.domain.exception.RikikiVaultException;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.RecipientKeyEntry;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;

import java.util.Objects;

public final class DecryptFileService implements DecryptFileUseCase {

    private final EncryptionPort encryptionPort;

    public DecryptFileService(EncryptionPort encryptionPort) {
        this.encryptionPort = Objects.requireNonNull(encryptionPort, "encryptionPort must not be null");
    }

    @Override
    public PlaintextFile decrypt(DecryptFileCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // Cheap fail-fast check: the application layer has the identity's full public key
        // (unlike EncryptionPort.decrypt, which only gets a bare PrivateKey), so it can rule
        // out an unauthorized machine before touching any cryptography at all.
        KeyFingerprint identityFingerprint = command.identity().id();
        boolean isAuthorizedRecipient = command.file().recipientEntries().stream()
                .map(RecipientKeyEntry::recipientFingerprint)
                .anyMatch(identityFingerprint::equals);
        if (!isAuthorizedRecipient) {
            throw new UnauthorizedMachineException(
                    "Machine identity " + identityFingerprint + " is not an authorized recipient of this file");
        }

        try {
            return encryptionPort.decrypt(command.file(), command.identity().privateKey());
        } catch (RikikiVaultException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DecryptionException("Failed to decrypt file", e);
        }
    }
}
