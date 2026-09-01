package io.github.jlmc.rikikivault.core.domain.exception;

public class CorruptedEncryptedFileException extends DecryptionException {

    public CorruptedEncryptedFileException(String message) {
        super(message);
    }

    public CorruptedEncryptedFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
