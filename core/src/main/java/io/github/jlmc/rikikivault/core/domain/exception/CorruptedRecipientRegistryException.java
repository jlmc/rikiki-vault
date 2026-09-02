package io.github.jlmc.rikikivault.core.domain.exception;

public class CorruptedRecipientRegistryException extends RikikiVaultException {

    public CorruptedRecipientRegistryException(String message) {
        super(message);
    }

    public CorruptedRecipientRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
