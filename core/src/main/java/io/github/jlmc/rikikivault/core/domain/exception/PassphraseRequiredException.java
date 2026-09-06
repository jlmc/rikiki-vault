package io.github.jlmc.rikikivault.core.domain.exception;

public class PassphraseRequiredException extends RikikiVaultException {

    public PassphraseRequiredException(String message) {
        super(message);
    }

    public PassphraseRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
