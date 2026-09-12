package io.github.jlmc.rikikivault.gui.support;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public final class Dialogs {

    private Dialogs() {
    }

    /** Walks to the deepest cause with its own distinct message - same composition {@link Notifications#error(Throwable)} uses. */
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
