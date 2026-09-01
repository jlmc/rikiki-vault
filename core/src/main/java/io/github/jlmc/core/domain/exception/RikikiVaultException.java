package io.github.jlmc.core.domain.exception;

public abstract class RikikiVaultException extends RuntimeException {

    protected RikikiVaultException(String message) {
        super(message);
    }

    protected RikikiVaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
