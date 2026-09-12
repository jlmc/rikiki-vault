package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

/**
 * The "Aplicação" section of the Settings screen - application-wide behaviour not tied to a
 * specific vault (today: notification auto-dismiss). Pure UI, same shape as
 * {@link GitAuthSettingsPanel}: never touches persistence itself, the owning
 * {@link SettingsController} hands it the settings to display via {@link #init} and reads back
 * the edited value via {@link #buildSettings()} when the user saves.
 */
public final class AppSettingsPanel {

    @FXML private CheckBox autoDismissCheckBox;
    @FXML private Spinner<Integer> autoDismissSecondsSpinner;

    void init(NotificationSettings settings) {
        autoDismissCheckBox.setSelected(settings.autoDismiss());
        autoDismissSecondsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 30, settings.autoDismissSeconds()));
        autoDismissSecondsSpinner.disableProperty().bind(autoDismissCheckBox.selectedProperty().not());
    }

    NotificationSettings buildSettings() {
        return new NotificationSettings(autoDismissCheckBox.isSelected(), autoDismissSecondsSpinner.getValue());
    }
}
