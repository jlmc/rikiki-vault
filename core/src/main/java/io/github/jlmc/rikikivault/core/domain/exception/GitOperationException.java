package io.github.jlmc.rikikivault.core.domain.exception;

public class GitOperationException extends RikikiVaultException {

    public GitOperationException(String message) {
        super(message);
    }

    public GitOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
