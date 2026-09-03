package io.github.jlmc.rikikivault.core.domain.exception;

/**
 * The remote a {@code clone} targeted has no recipients - a validly {@code init}-ed vault always
 * has at least one (the machine that created it), even with zero files published yet. Distinct
 * from {@link CorruptedRecipientRegistryException}, which is for a registry that fails to parse -
 * this one is well-formed but legitimately empty, meaning the remote was never initialized.
 */
public class UninitializedVaultException extends RikikiVaultException {

    public UninitializedVaultException(String message) {
        super(message);
    }
}
