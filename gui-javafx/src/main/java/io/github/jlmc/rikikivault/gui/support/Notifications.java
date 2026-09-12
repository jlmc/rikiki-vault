package io.github.jlmc.rikikivault.gui.support;

import io.github.jlmc.rikikivault.core.configuration.NotificationPosition;
import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-blocking toast notifications shown in a configurable screen corner, replacing every
 * {@code Dialogs.showInfo}/{@code showWarning}/{@code showError} in the app (only
 * {@link Dialogs#confirm} remains - that's a blocking decision, not a completion notice). Static,
 * same shape as {@link Dialogs} - any controller calls {@code success}/{@code info}/
 * {@code warning}/{@code error} without holding a reference to the notification panel; only
 * {@link #createHost} (called once, from {@code App.java}) needs to know this class exists at
 * all, and even that call gets back an opaque {@link Region} - the panel's internal Node
 * structure (VBox/StackPane/alignment) is this class's own implementation detail, never the
 * caller's concern.
 */
public final class Notifications {

    private static final Logger log = LoggerFactory.getLogger(Notifications.class);

    private static VBox toastStack;
    private static NotificationSettings currentSettings = NotificationSettings.empty();

    private Notifications() {
    }

    /**
     * Builds the toast host - a persistent, always-visible overlay anchored to
     * {@code settings.position()} - and wires it as the target every {@code success}/
     * {@code info}/{@code warning}/{@code error} call adds its toast to. Called once, from
     * {@code App.start(...)}: the returned {@link Region} just needs to be added to the Scene
     * graph above whatever screen is currently showing (e.g. as a sibling in a root
     * {@code StackPane}) - the caller never needs to know it's a VBox inside a StackPane, or how
     * the corner anchoring works.
     */
    public static Region createHost(NotificationSettings settings) {
        toastStack = new VBox(8);
        toastStack.setPickOnBounds(false);

        StackPane host = new StackPane(toastStack);
        // pickOnBounds(false) on the empty wrapper lets clicks pass through to the screen
        // underneath; the toasts themselves (children of toastStack) stay clickable.
        host.setPickOnBounds(false);
        host.getStyleClass().add("notifications-pane");

        currentSettings = settings;
        applyPosition(settings.position());
        return host;
    }

    /**
     * Lets notifications shown from now on respect a preference change saved without restarting
     * the app - a toast already on screen keeps whatever position/behavior it started with.
     */
    public static void updateSettings(NotificationSettings settings) {
        currentSettings = settings;
        applyPosition(settings.position());
    }

    /**
     * Layout panes default to expanding to fill their parent's full content area (unlike
     * Controls, which default to their preferred size) - with no bound on its own max size, this
     * VBox stretches to fill the entire window, which would make a StackPane alignment
     * constraint on it a no-op (there's no leftover space left to align it *within*). What
     * actually positions the toasts is this VBox's own alignment, governing where it lays out
     * its children inside its own (now window-sized) bounds.
     */
    private static void applyPosition(NotificationPosition position) {
        if (toastStack == null) {
            return;
        }
        toastStack.setAlignment(toPos(position));
    }

    private static Pos toPos(NotificationPosition position) {
        return switch (position) {
            case TOP_LEFT -> Pos.TOP_LEFT;
            case TOP_RIGHT -> Pos.TOP_RIGHT;
            case BOTTOM_LEFT -> Pos.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> Pos.BOTTOM_RIGHT;
        };
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
        if (toastStack == null) {
            return;
        }
        NotificationToast.show(toastStack, type, message, currentSettings);
    }
}
