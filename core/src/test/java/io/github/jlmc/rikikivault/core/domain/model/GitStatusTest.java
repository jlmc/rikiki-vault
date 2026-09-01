package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitStatusTest {

    private static GitStatus empty() {
        return new GitStatus(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    @Test
    void isCleanWhenEverySetIsEmpty() {
        assertTrue(empty().isClean());
    }

    @Test
    void isNotCleanWhenAddedIsNonEmpty() {
        GitStatus status = new GitStatus(Set.of("a.txt"), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

        assertFalse(status.isClean());
    }

    @Test
    void isNotCleanWhenUntrackedIsNonEmpty() {
        GitStatus status = new GitStatus(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of("new.txt"), Set.of());

        assertFalse(status.isClean());
    }

    @Test
    void isNotCleanWhenConflictingIsNonEmpty() {
        GitStatus status = new GitStatus(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of("c.txt"));

        assertFalse(status.isClean());
    }
}
