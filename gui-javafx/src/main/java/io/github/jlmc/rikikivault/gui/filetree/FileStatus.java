package io.github.jlmc.rikikivault.gui.filetree;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;

/** Status badge shown per file in the browser and in the change review list. */
public enum FileStatus {

    SYNCED("✓", "status-synced"),
    ADDED("+", "status-added"),
    MODIFIED("M", "status-modified"),
    DELETED("✕", "status-deleted");

    private final String symbol;
    private final String styleClass;

    FileStatus(String symbol, String styleClass) {
        this.symbol = symbol;
        this.styleClass = styleClass;
    }

    String symbol() {
        return symbol;
    }

    String styleClass() {
        return styleClass;
    }

    public static FileStatus from(VaultChange.ChangeType type) {
        return switch (type) {
            case ADDED -> ADDED;
            case MODIFIED -> MODIFIED;
            case DELETED -> DELETED;
        };
    }
}
