package io.github.jlmc.rikikivault.gui.filetree;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;

/** Status badge shown per file in the browser and in the change review list. */
public enum FileStatus {

    SYNCED("fth-check", "status-synced"),
    ADDED("fth-plus", "status-added"),
    MODIFIED("fth-edit-2", "status-modified"),
    DELETED("fth-x", "status-deleted");

    private final String iconLiteral;
    private final String styleClass;

    FileStatus(String iconLiteral, String styleClass) {
        this.iconLiteral = iconLiteral;
        this.styleClass = styleClass;
    }

    String iconLiteral() {
        return iconLiteral;
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
