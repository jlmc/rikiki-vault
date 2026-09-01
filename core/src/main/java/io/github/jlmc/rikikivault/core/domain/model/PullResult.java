package io.github.jlmc.rikikivault.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a {@code pull} (Plan.md §11): which remotely-changed paths were safely applied
 * to {@code local/}, which conflicted with an uncommitted local change and were left untouched,
 * and the full set of local changes detected before the pull (for a "you have uncommitted
 * changes" warning, independent of whether any of them actually conflicted).
 */
public record PullResult(
        List<String> updatedPaths,
        List<String> deletedPaths,
        List<VaultConflict> conflicts,
        List<VaultChange> uncommittedLocalChangesAtStart) {

    public PullResult {
        Objects.requireNonNull(updatedPaths, "updatedPaths must not be null");
        Objects.requireNonNull(deletedPaths, "deletedPaths must not be null");
        Objects.requireNonNull(conflicts, "conflicts must not be null");
        Objects.requireNonNull(uncommittedLocalChangesAtStart, "uncommittedLocalChangesAtStart must not be null");
        updatedPaths = List.copyOf(updatedPaths);
        deletedPaths = List.copyOf(deletedPaths);
        conflicts = List.copyOf(conflicts);
        uncommittedLocalChangesAtStart = List.copyOf(uncommittedLocalChangesAtStart);
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
