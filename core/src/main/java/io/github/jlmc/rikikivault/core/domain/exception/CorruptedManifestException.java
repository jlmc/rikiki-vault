package io.github.jlmc.rikikivault.core.domain.exception;

public class CorruptedManifestException extends RikikiVaultException {

    public CorruptedManifestException(String message) {
        super(message);
    }

    public CorruptedManifestException(String message, Throwable cause) {
        super(message, cause);
    }
}
