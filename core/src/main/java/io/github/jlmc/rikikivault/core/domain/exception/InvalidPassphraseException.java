package io.github.jlmc.rikikivault.core.domain.exception;

public class InvalidPassphraseException extends RikikiVaultException {

    public InvalidPassphraseException(String message) {
        super(message);
    }

    public InvalidPassphraseException(String message, Throwable cause) {
        super(message, cause);
    }
}
