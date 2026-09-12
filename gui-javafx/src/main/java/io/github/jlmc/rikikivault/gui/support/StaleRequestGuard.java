package io.github.jlmc.rikikivault.gui.support;

/**
 * Tags each new asynchronous request with a monotonically increasing token so a callback for a
 * superseded request can tell it is stale and discard its result, instead of clobbering newer
 * state. Not thread-safe - both next() and isStale(long) must be called from the same thread (in
 * this codebase, always the JavaFX Application Thread, matching BackgroundTasks' callback
 * dispatch).
 * A plain counter, not a timestamp: only relative order matters ("is this the latest?"), and
 * unlike System.currentTimeMillis() a counter can't run backward across a clock adjustment; a
 * String/UUID would carry no ordering by itself, so it wouldn't help either.
 */
public final class StaleRequestGuard {

    private long currentToken;

    /** Starts a new request; the caller should close over the returned token in its callback(s). */
    public long next() {
        return ++currentToken;
    }

    /** True when {@code token} no longer identifies the latest request started via next(). */
    public boolean isStale(long token) {
        return token != currentToken;
    }
}
