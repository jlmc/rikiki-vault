package io.github.jlmc.core.domain.exception;

public class DecryptionException extends RikikiVaultException {

    public DecryptionException(String message) {
        super(message);
    }

    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
