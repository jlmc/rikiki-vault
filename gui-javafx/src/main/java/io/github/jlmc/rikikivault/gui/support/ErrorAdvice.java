package io.github.jlmc.rikikivault.gui.support;

import io.github.jlmc.rikikivault.core.domain.exception.CorruptedManifestException;
import io.github.jlmc.rikikivault.core.domain.exception.CorruptedRecipientRegistryException;
import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.exception.UninitializedVaultException;
import io.github.jlmc.rikikivault.core.domain.exception.VaultAlreadyInitializedException;

import java.util.Locale;

/**
 * A curated (explanation, suggestion) pair for a known failure mode (Milestone 21), with a
 * generic fallback for anything unrecognized - so {@link Dialogs#showError(Throwable)} always has
 * something concrete to say beyond the raw exception message, and can never end up with blank
 * content even when {@code error.getMessage()} itself is blank. Matches by exception type where
 * one exists in this app's domain, and by a substring of the root cause's message for failures
 * that only ever surface as a generic {@code GitOperationException} wrapping a JGit/network
 * exception (there's no dedicated type to match on for "SSH auth failed" or "host unreachable").
 */
record ErrorAdvice(String explanation, String suggestion) {

    static ErrorAdvice forError(Throwable error) {
        if (findInChain(error, UnauthorizedMachineException.class) != null) {
            return new ErrorAdvice(Messages.get("errorAdvice.unauthorizedMachine.explanation"),
                    Messages.get("errorAdvice.unauthorizedMachine.suggestion"));
        }
        if (findInChain(error, MachineIdentityAlreadyExistsException.class) != null) {
            return new ErrorAdvice(Messages.get("errorAdvice.machineIdentityExists.explanation"),
                    Messages.get("errorAdvice.machineIdentityExists.suggestion"));
        }
        if (findInChain(error, UninitializedVaultException.class) != null) {
            return new ErrorAdvice(Messages.get("errorAdvice.uninitializedVault.explanation"),
                    Messages.get("errorAdvice.uninitializedVault.suggestion"));
        }
        if (findInChain(error, VaultAlreadyInitializedException.class) != null) {
            return new ErrorAdvice(Messages.get("errorAdvice.vaultAlreadyInitialized.explanation"),
                    Messages.get("errorAdvice.vaultAlreadyInitialized.suggestion"));
        }
        if (findInChain(error, CorruptedManifestException.class) != null
                || findInChain(error, CorruptedRecipientRegistryException.class) != null) {
            return new ErrorAdvice(Messages.get("errorAdvice.corruptedData.explanation"),
                    Messages.get("errorAdvice.corruptedData.suggestion"));
        }

        String rootMessage = rootMessageOf(error).toLowerCase(Locale.ROOT);
        if (rootMessage.contains("authentication") || rootMessage.contains("not authorized")) {
            return new ErrorAdvice(Messages.get("errorAdvice.gitAuth.explanation"), Messages.get("errorAdvice.gitAuth.suggestion"));
        }
        if (rootMessage.contains("unknownhost") || rootMessage.contains("connect") || rootMessage.contains("timed out")
                || rootMessage.contains("timeout") || rootMessage.contains("network")) {
            return new ErrorAdvice(Messages.get("errorAdvice.network.explanation"), Messages.get("errorAdvice.network.suggestion"));
        }

        return new ErrorAdvice(Messages.get("errorAdvice.generic.explanation"), Messages.get("errorAdvice.generic.suggestion"));
    }

    private static <T extends Throwable> T findInChain(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String rootMessageOf(Throwable error) {
        Throwable rootCause = error;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.toString();
    }
}
