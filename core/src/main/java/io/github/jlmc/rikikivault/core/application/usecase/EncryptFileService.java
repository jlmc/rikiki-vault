package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.EncryptionException;
import io.github.jlmc.rikikivault.core.domain.exception.RikikiVaultException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.ports.in.EncryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.EncryptFileUseCase;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;

import java.util.Objects;

public final class EncryptFileService implements EncryptFileUseCase {

    private final EncryptionPort encryptionPort;

    public EncryptFileService(EncryptionPort encryptionPort) {
        this.encryptionPort = Objects.requireNonNull(encryptionPort, "encryptionPort must not be null");
    }

    @Override
    public EncryptedFile encrypt(EncryptFileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        try {
            return encryptionPort.encrypt(command.file(), command.recipients());
        } catch (RikikiVaultException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EncryptionException("Failed to encrypt file", e);
        }
    }
}
