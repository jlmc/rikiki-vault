package io.github.jlmc.rikikivault.core.domain.exception;

public class CorruptedPrivateKeyEnvelopeException extends RikikiVaultException {

    public CorruptedPrivateKeyEnvelopeException(String message) {
        super(message);
    }

    public CorruptedPrivateKeyEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
