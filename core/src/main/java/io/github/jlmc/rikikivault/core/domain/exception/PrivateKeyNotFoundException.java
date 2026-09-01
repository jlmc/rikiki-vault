package io.github.jlmc.rikikivault.core.domain.exception;

public class PrivateKeyNotFoundException extends RikikiVaultException {

    public PrivateKeyNotFoundException(String message) {
        super(message);
    }

    public PrivateKeyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
