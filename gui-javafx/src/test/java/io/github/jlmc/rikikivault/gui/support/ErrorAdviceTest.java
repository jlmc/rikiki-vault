package io.github.jlmc.rikikivault.gui.support;

import io.github.jlmc.rikikivault.core.domain.exception.GitOperationException;
import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.exception.UninitializedVaultException;
import io.github.jlmc.rikikivault.core.domain.exception.VaultAlreadyInitializedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErrorAdviceTest {

    @Test
    void matchesUnauthorizedMachineByType() {
        ErrorAdvice advice = ErrorAdvice.forError(new UnauthorizedMachineException("nope"));

        assertEquals(Messages.get("errorAdvice.unauthorizedMachine.explanation"), advice.explanation());
        assertEquals(Messages.get("errorAdvice.unauthorizedMachine.suggestion"), advice.suggestion());
    }

    @Test
    void matchesMachineIdentityAlreadyExistsByType() {
        ErrorAdvice advice = ErrorAdvice.forError(new MachineIdentityAlreadyExistsException("nope"));

        assertEquals(Messages.get("errorAdvice.machineIdentityExists.explanation"), advice.explanation());
    }

    @Test
    void matchesUninitializedVaultByType() {
        ErrorAdvice advice = ErrorAdvice.forError(new UninitializedVaultException("nope"));

        assertEquals(Messages.get("errorAdvice.uninitializedVault.explanation"), advice.explanation());
    }

    @Test
    void matchesVaultAlreadyInitializedByType() {
        ErrorAdvice advice = ErrorAdvice.forError(new VaultAlreadyInitializedException("nope"));

        assertEquals(Messages.get("errorAdvice.vaultAlreadyInitialized.explanation"), advice.explanation());
    }

    @Test
    void matchesGitAuthFailureByRootCauseMessage() {
        Throwable jgitStyleFailure = new RuntimeException("No more authentication methods available");
        ErrorAdvice advice = ErrorAdvice.forError(new GitOperationException("Failed to push", jgitStyleFailure));

        assertEquals(Messages.get("errorAdvice.gitAuth.explanation"), advice.explanation());
    }

    @Test
    void matchesNetworkFailureByRootCauseMessage() {
        Throwable networkFailure = new RuntimeException("Connection timed out");
        ErrorAdvice advice = ErrorAdvice.forError(new GitOperationException("Failed to fetch", networkFailure));

        assertEquals(Messages.get("errorAdvice.network.explanation"), advice.explanation());
    }

    @Test
    void unrecognizedErrorFallsBackToGeneric() {
        ErrorAdvice advice = ErrorAdvice.forError(new RuntimeException("something completely unrelated"));

        assertEquals(Messages.get("errorAdvice.generic.explanation"), advice.explanation());
        assertEquals(Messages.get("errorAdvice.generic.suggestion"), advice.suggestion());
    }

    @Test
    void neverReturnsBlankAdviceEvenForAnExceptionWithNoMessageAndNoCause() {
        ErrorAdvice advice = ErrorAdvice.forError(new RuntimeException((String) null));

        assertFalse(advice.explanation().isBlank());
        assertFalse(advice.suggestion().isBlank());
    }
}
