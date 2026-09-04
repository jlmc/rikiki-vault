package io.github.jlmc.rikikivault.gui.support;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class Dialogs {

    private static final Logger log = LoggerFactory.getLogger(Dialogs.class);

    private Dialogs() {
    }

    /**
     * Every controller in this module funnels its errors through here, so logging
     * the full stack trace once, in this single spot, captures every error dialog in the app -
     * useful when the app is packaged (jpackage) and there's no visible console, but
     * {@code scripts/run.sh gui} runs in the terminal that launched it.
     */
    public static void showError(Throwable error) {
        log.error("Showing error dialog", error);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Messages.get("common.error.title"));
        alert.setHeaderText(null);
        ErrorAdvice advice = ErrorAdvice.forError(error);
        String content = fullMessage(error)
                + "\n\n" + Messages.get("errorAdvice.why") + ": " + advice.explanation()
                + "\n" + Messages.get("errorAdvice.suggestion") + ": " + advice.suggestion();
        // advice.explanation()/suggestion() always resolve to a real bundle entry, so content here
        // can never actually be blank - safe() is just the same net showInfo/showWarning use, kept
        // consistent rather than assumed.
        alert.setContentText(safe(content, Messages.get("errorAdvice.generic.explanation")));
        alert.showAndWait();
    }

    public static void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(safe(title, Messages.get("common.warning.title")));
        alert.setHeaderText(null);
        alert.setContentText(safe(message, Messages.get("dialogs.fallback.warning")));
        alert.showAndWait();
    }

    /** Walks to the deepest cause with its own distinct message - same composition {@link #showError} uses. */
    public static String fullMessage(Throwable error) {
        String topMessage = error.getMessage() != null ? error.getMessage() : error.toString();
        Throwable rootCause = error;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String rootMessage = rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.toString();
        return rootCause == error || rootMessage.equals(topMessage)
                ? topMessage
                : topMessage + "\n\n" + Messages.get("common.cause") + ": " + rootMessage;
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(safe(message, Messages.get("dialogs.fallback.info")));
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(safe(message, Messages.get("dialogs.fallback.info")));
        alert.showAndWait();
    }

    /**
     * Defensive net (Milestone 21): whatever the cause, no {@code Alert} built through this class
     * can ever render with blank content - a reported bug the exact trigger of which was never
     * pinned down, so this closes off the whole class of "some string ended up empty" causes
     * rather than one specific one.
     */
    private static String safe(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }
}
