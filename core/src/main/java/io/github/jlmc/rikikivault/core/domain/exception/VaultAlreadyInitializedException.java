package io.github.jlmc.rikikivault.core.domain.exception;

/**
 * The target of an {@code init} already has recipients - it was already initialized (by this
 * machine or another one whose changes were pulled in), so re-running {@code init} would silently
 * wipe out its manifest/recipient registry. The counterpart check to
 * {@link UninitializedVaultException}, which guards {@code clone} the other way around.
 */
public class VaultAlreadyInitializedException extends RikikiVaultException {

    public VaultAlreadyInitializedException(String message) {
        super(message);
    }
}
