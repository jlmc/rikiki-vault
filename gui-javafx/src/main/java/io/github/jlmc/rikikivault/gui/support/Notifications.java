package io.github.jlmc.rikikivault.gui.support;

import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-blocking toast notifications shown in the bottom-right corner, replacing every
 * {@code Dialogs.showInfo}/{@code showWarning}/{@code showError} in the app (only
 * {@link Dialogs#confirm} remains - that's a blocking decision, not a completion notice). Static,
 * same shape as {@link Dialogs} - any controller calls {@code success}/{@code info}/
 * {@code warning}/{@code error} without holding a reference to the notification panel; only
 * {@link #attach} (called once, from {@code App.java}) needs the real container Node.
 */
public final class Notifications {

    private static final Logger log = LoggerFactory.getLogger(Notifications.class);

    private static Pane container;
    private static NotificationSettings currentSettings = NotificationSettings.empty();

    private Notifications() {
    }

    /** Wires the real container Node in the scene graph - called once, from {@code App.start(...)}. */
    public static void attach(Pane container, NotificationSettings settings) {
        Notifications.container = container;
        Notifications.currentSettings = settings;
    }

    /**
     * Lets notifications shown from now on respect a preference change saved without restarting
     * the app - a toast already on screen keeps whatever behavior it started with.
     */
    public static void updateSettings(NotificationSettings settings) {
        currentSettings = settings;
    }

    public static void success(String message) {
        show(NotificationType.SUCCESS, message);
    }

    public static void info(String message) {
        show(NotificationType.INFO, message);
    }

    public static void warning(String message) {
        show(NotificationType.WARNING, message);
    }

    public static void error(String message) {
        show(NotificationType.ERROR, message);
    }

    /**
     * Every controller in this module funnels its errors through here, so logging the full stack
     * trace once, in this single spot, captures every error notification in the app - useful when
     * the app is packaged (jpackage) and there's no visible console, but {@code scripts/run.sh gui}
     * runs in the terminal that launched it.
     */
    public static void error(Throwable error) {
        log.error("Showing error notification", error);
        ErrorAdvice advice = ErrorAdvice.forError(error);
        String content = Dialogs.fullMessage(error)
                + "\n\n" + Messages.get("errorAdvice.why") + ": " + advice.explanation()
                + "\n" + Messages.get("errorAdvice.suggestion") + ": " + advice.suggestion();
        show(NotificationType.ERROR, content);
    }

    private static void show(NotificationType type, String message) {
        if (container == null) {
            return;
        }
        NotificationToast.show(container, type, message, currentSettings);
    }
}
