package io.github.jlmc.rikikivault.core.configuration;

/**
 * Which of the explicit Git authentication methods configured in {@link GitAuthSettings} is
 * actually applied. Exactly one is active at a time - credentials for the others may still be
 * stored (so switching back and forth never loses them), but only the active one is ever used by
 * {@code JGitRepositoryAdapter} to resolve credentials/SSH identity.
 */
public enum GitAuthType {
    NONE,
    SSH,
    TOKEN,
    HTTP_BASIC
}
