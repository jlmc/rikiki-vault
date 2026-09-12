package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.NotificationPosition;
import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import io.github.jlmc.rikikivault.gui.support.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

/**
 * The "Aplicação" section of the Settings screen - application-wide preferences not tied to a
 * specific vault (application language, notification auto-dismiss + screen corner). Pure UI,
 * same shape as {@link GitAuthSettingsPanel}: never touches persistence itself, the owning
 * {@link SettingsController} hands it the settings to display via {@link #init} and reads back
 * the edited values via {@link #selectedLanguage()}/{@link #buildNotificationSettings()} when the
 * user saves.
 */
public final class AppSettingsPanel {

    @FXML private RadioButton ptRadio;
    @FXML private RadioButton enRadio;
    @FXML private CheckBox autoDismissCheckBox;
    @FXML private Spinner<Integer> autoDismissSecondsSpinner;
    @FXML private ComboBox<NotificationPosition> positionComboBox;

    void init(AppLanguage language, NotificationSettings notifications) {
        (language == AppLanguage.EN ? enRadio : ptRadio).setSelected(true);

        autoDismissCheckBox.setSelected(notifications.autoDismiss());
        autoDismissSecondsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 30, notifications.autoDismissSeconds()));
        autoDismissSecondsSpinner.disableProperty().bind(autoDismissCheckBox.selectedProperty().not());

        positionComboBox.getItems().setAll(NotificationPosition.values());
        positionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(NotificationPosition position) {
                return Messages.get(labelKey(position));
            }

            @Override
            public NotificationPosition fromString(String label) {
                throw new UnsupportedOperationException("not editable");
            }
        });
        positionComboBox.setValue(notifications.position());
    }

    AppLanguage selectedLanguage() {
        return enRadio.isSelected() ? AppLanguage.EN : AppLanguage.PT;
    }

    NotificationSettings buildNotificationSettings() {
        return new NotificationSettings(
                autoDismissCheckBox.isSelected(), autoDismissSecondsSpinner.getValue(), positionComboBox.getValue());
    }

    private static String labelKey(NotificationPosition position) {
        return switch (position) {
            case TOP_LEFT -> "appSettingsPanel.position.topLeft";
            case TOP_RIGHT -> "appSettingsPanel.position.topRight";
            case BOTTOM_LEFT -> "appSettingsPanel.position.bottomLeft";
            case BOTTOM_RIGHT -> "appSettingsPanel.position.bottomRight";
        };
    }
}
