package io.github.jlmc.rikikivault.core.configuration;

import java.nio.file.Path;

/**
 * Explicit Git authentication overrides the user can configure in the app, instead of relying
 * entirely on implicit discovery (SSH agent/default-named keys/{@code ~/.ssh/config}, or the
 * {@code RIKIKI_VAULT_GITHUB_TOKEN} env var). All three credential sets may be stored at once -
 * only {@link #activeType()} decides which one is actually applied, so trying another method
 * never discards what was already configured for the others.
 */
public record GitAuthSettings(
        GitAuthType activeType,
        Path sshPrivateKeyPath,
        String githubToken,
        String httpUsername,
        String httpPassword
) {

    public static GitAuthSettings empty() {
        return new GitAuthSettings(GitAuthType.NONE, null, null, null, null);
    }
}
