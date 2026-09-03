package io.github.jlmc.rikikivault.core.configuration;

import java.nio.file.Path;

/**
 * Explicit Git authentication overrides the user can configure in the app, instead of relying
 * entirely on implicit discovery (SSH agent/default-named keys/{@code ~/.ssh/config}, or the
 * {@code RIKIKI_VAULT_GITHUB_TOKEN} env var). Either field may be {@code null} - the remote URL's
 * own scheme (ssh vs https) already decides which one, if any, applies to a given operation.
 */
public record GitAuthSettings(Path sshPrivateKeyPath, String githubToken) {

    public static GitAuthSettings empty() {
        return new GitAuthSettings(null, null);
    }
}
