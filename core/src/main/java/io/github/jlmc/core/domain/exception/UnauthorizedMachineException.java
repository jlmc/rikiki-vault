package io.github.jlmc.core.domain.exception;

public class UnauthorizedMachineException extends DecryptionException {

    public UnauthorizedMachineException(String message) {
        super(message);
    }

    public UnauthorizedMachineException(String message, Throwable cause) {
        super(message, cause);
    }
}
