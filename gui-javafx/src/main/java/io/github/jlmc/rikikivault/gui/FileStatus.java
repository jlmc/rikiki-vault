package io.github.jlmc.rikikivault.gui;

/** Status badge shown per file in the browser (Plan.md §13). */
enum FileStatus {

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
}
