package io.github.jlmc.core.domain.exception;

public class EncryptionException extends RikikiVaultException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
