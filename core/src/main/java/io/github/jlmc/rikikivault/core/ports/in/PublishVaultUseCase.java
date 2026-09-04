package io.github.jlmc.rikikivault.core.ports.in;

public interface PublishVaultUseCase {

    /**
     * Encrypts, updates the manifest, commits, and pushes if a remote is configured - the
     * combined flow used by the CLI. {@link #publishLocally(PublishVaultCommand)} and
     * {@link #pushToRemote()} are the two halves of this, callable separately so a caller (the
     * GUI) can commit locally first - a step that must never fail because of the remote - and
     * only attempt the push as an explicit, optional second step.
     */
    boolean publish(PublishVaultCommand command);

    /** Encrypts, updates the manifest and commits - never touches the network. */
    void publishLocally(PublishVaultCommand command);

    /** @return {@code true} if pushed, {@code false} if there is no remote configured. */
    boolean pushToRemote();
}
