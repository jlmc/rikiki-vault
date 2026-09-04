package io.github.jlmc.rikikivault.core.domain.model;

/**
 * Best-effort snapshot of how the local branch compares to its remote counterpart, meant for an
 * on-demand/informational check (e.g. a GUI status badge) - never wired into a blocking flow,
 * since computing it always requires a network round-trip that can fail for reasons unrelated to
 * the vault itself (no network, broken credentials, ...).
 */
public record RemoteSyncStatus(boolean hasRemote, int aheadCount, int behindCount) {

    public static RemoteSyncStatus noRemote() {
        return new RemoteSyncStatus(false, 0, 0);
    }

    public boolean isSynced() {
        return hasRemote && aheadCount == 0 && behindCount == 0;
    }

    public boolean isDiverged() {
        return hasRemote && aheadCount > 0 && behindCount > 0;
    }
}
