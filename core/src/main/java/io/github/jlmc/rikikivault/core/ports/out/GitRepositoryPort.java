package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.model.GitStatus;
import io.github.jlmc.rikikivault.core.domain.model.RemoteSyncStatus;

import java.util.List;

public interface GitRepositoryPort {

    void init();

    void addRemote(String name, String url);

    void clone(String remoteUri);

    void pull();

    /**
     * Local-only check (reads the repository's own config, no network) - {@code true} if any
     * remote is configured. Safe to call from a flow that must never fail because of a broken
     * network/authentication, unlike {@link #remoteSyncStatus()}.
     */
    boolean hasRemote();

    /**
     * Fetches from the remote and compares it to the local branch. A best-effort, on-demand check
     * (e.g. for a status badge) - never wire this into a flow that must succeed even without
     * network access or working credentials; call {@link #hasRemote()} first if that's needed.
     *
     * @return {@link RemoteSyncStatus#noRemote()} when there's no remote configured
     */
    RemoteSyncStatus remoteSyncStatus();

    GitStatus status();

    void add(List<String> relativePaths);

    void commit(String message);

    /**
     * @return {@code true} if the commit(s) were pushed, {@code false} if there is no remote
     * configured yet and the push was skipped (a valid local-only vault, not an error).
     */
    boolean push();

    String diff();
}
