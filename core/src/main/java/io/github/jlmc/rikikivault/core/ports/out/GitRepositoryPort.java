package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.model.GitStatus;

import java.util.List;

public interface GitRepositoryPort {

    void init();

    void addRemote(String name, String url);

    void clone(String remoteUri);

    void pull();

    /**
     * @return {@code true} if the remote has commits this local branch doesn't have yet (fetched
     * but not merged in) - {@code false} when there's no remote configured, so callers don't need
     * to special-case a local-only vault themselves.
     */
    boolean isRemoteAhead();

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
