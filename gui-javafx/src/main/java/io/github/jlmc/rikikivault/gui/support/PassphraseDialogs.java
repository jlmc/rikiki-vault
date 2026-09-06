package io.github.jlmc.rikikivault.gui.support;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;

import java.util.Arrays;
import java.util.Optional;

/**
 * A small modal "set a new passphrase" prompt (new + confirm, validated on OK before the dialog
 * closes) - used where offering passphrase protection is a one-off, opportunistic step (right
 * after creating a new machine identity), not the main unlock/settings screens, which have their
 * own dedicated FXML views with the masked-field-plus-reveal-eye pattern.
 */
public final class PassphraseDialogs {

    private PassphraseDialogs() {
    }

    public static Optional<char[]> promptNewPassphrase() {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(Messages.get("passphraseDialog.title"));
        dialog.setHeaderText(Messages.get("passphraseDialog.header"));

        ButtonType okButtonType = new ButtonType(Messages.get("common.ok"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        PasswordField newField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        Label errorLabel = new Label();
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setStyle("-fx-text-fill: -color-danger-fg, #c0392b;");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label(Messages.get("passphraseDialog.newLabel")), newField);
        grid.addRow(1, new Label(Messages.get("passphraseDialog.confirmLabel")), confirmField);
        grid.add(errorLabel, 0, 2, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(okButtonType);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            char[] first = newField.getText().toCharArray();
            char[] second = confirmField.getText().toCharArray();
            if (first.length == 0) {
                showInlineError(errorLabel, Messages.get("passphraseDialog.empty"));
                event.consume();
            } else if (!Arrays.equals(first, second)) {
                showInlineError(errorLabel, Messages.get("passphraseDialog.mismatch"));
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType ->
                buttonType == okButtonType ? newField.getText().toCharArray() : null);

        return dialog.showAndWait();
    }

    private static void showInlineError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
