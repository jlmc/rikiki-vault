package io.github.jlmc.rikikivault.core.domain.exception;

public class MachineIdentityAlreadyExistsException extends RikikiVaultException {

    public MachineIdentityAlreadyExistsException(String message) {
        super(message);
    }

    public MachineIdentityAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
