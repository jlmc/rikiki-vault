package io.github.jlmc.rikikivault.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

final class Dialogs {

    private Dialogs() {
    }

    static void showError(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(Messages.get("common.error.title"));
        alert.setHeaderText(null);
        alert.setContentText(fullMessage(error));
        alert.showAndWait();
    }

    static void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Walks to the deepest cause with its own distinct message - same composition {@link #showError} uses. */
    static String fullMessage(Throwable error) {
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

    static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
