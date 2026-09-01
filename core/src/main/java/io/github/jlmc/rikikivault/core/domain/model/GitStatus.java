package io.github.jlmc.rikikivault.core.domain.model;

import java.util.Objects;
import java.util.Set;

/**
 * A snapshot of a Git working tree's state, mirroring JGit's own status categories 1:1 rather
 * than collapsing staged/unstaged into a single ADDED/MODIFIED/DELETED view like
 * {@link VaultChange} does — staged vs unstaged is a real, distinct Git concept worth preserving
 * here.
 */
public record GitStatus(
        Set<String> added,
        Set<String> changed,
        Set<String> removed,
        Set<String> modified,
        Set<String> missing,
        Set<String> untracked,
        Set<String> conflicting
) {

    public GitStatus {
        Objects.requireNonNull(added, "added must not be null");
        Objects.requireNonNull(changed, "changed must not be null");
        Objects.requireNonNull(removed, "removed must not be null");
        Objects.requireNonNull(modified, "modified must not be null");
        Objects.requireNonNull(missing, "missing must not be null");
        Objects.requireNonNull(untracked, "untracked must not be null");
        Objects.requireNonNull(conflicting, "conflicting must not be null");
        added = Set.copyOf(added);
        changed = Set.copyOf(changed);
        removed = Set.copyOf(removed);
        modified = Set.copyOf(modified);
        missing = Set.copyOf(missing);
        untracked = Set.copyOf(untracked);
        conflicting = Set.copyOf(conflicting);
    }

    public boolean isClean() {
        return added.isEmpty()
                && changed.isEmpty()
                && removed.isEmpty()
                && modified.isEmpty()
                && missing.isEmpty()
                && untracked.isEmpty()
                && conflicting.isEmpty();
    }
}
