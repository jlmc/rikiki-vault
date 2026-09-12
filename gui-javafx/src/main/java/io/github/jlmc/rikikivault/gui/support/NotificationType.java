package io.github.jlmc.rikikivault.gui.support;

/** Icon + CSS style class per notification kind, same pattern as {@code FileStatus}. */
enum NotificationType {
    SUCCESS("fth-check-circle", "notification-success"),
    ERROR("fth-alert-circle", "notification-error"),
    WARNING("fth-alert-triangle", "notification-warning"),
    INFO("fth-info", "notification-info");

    private final String iconLiteral;
    private final String styleClass;

    NotificationType(String iconLiteral, String styleClass) {
        this.iconLiteral = iconLiteral;
        this.styleClass = styleClass;
    }

    String iconLiteral() {
        return iconLiteral;
    }

    String styleClass() {
        return styleClass;
    }
}
