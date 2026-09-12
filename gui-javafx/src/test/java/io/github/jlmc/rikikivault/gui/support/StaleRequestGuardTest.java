package io.github.jlmc.rikikivault.gui.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaleRequestGuardTest {

    @Test
    void firstTokenIsNeverStaleImmediatelyAfterNext() {
        StaleRequestGuard guard = new StaleRequestGuard();

        long token = guard.next();

        assertFalse(guard.isStale(token));
    }

    @Test
    void aTokenBecomesStaleOnceANewerOneStarts() {
        StaleRequestGuard guard = new StaleRequestGuard();

        long first = guard.next();
        long second = guard.next();

        assertTrue(guard.isStale(first));
        assertFalse(guard.isStale(second));
    }
}
