package io.github.jlmc.rikikivault.gui.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTypeTest {

    @Test
    void success() {
        assertEquals("fth-check-circle", NotificationType.SUCCESS.iconLiteral());
        assertEquals("notification-success", NotificationType.SUCCESS.styleClass());
    }

    @Test
    void error() {
        assertEquals("fth-alert-circle", NotificationType.ERROR.iconLiteral());
        assertEquals("notification-error", NotificationType.ERROR.styleClass());
    }

    @Test
    void warning() {
        assertEquals("fth-alert-triangle", NotificationType.WARNING.iconLiteral());
        assertEquals("notification-warning", NotificationType.WARNING.styleClass());
    }

    @Test
    void info() {
        assertEquals("fth-info", NotificationType.INFO.iconLiteral());
        assertEquals("notification-info", NotificationType.INFO.styleClass());
    }
}
