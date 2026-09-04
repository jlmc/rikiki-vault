package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

/**
 * The "Git" section of the Settings screen (Milestone 19) - three explicit authentication
 * methods (SSH/Token/HTTP-basic), only one active at a time (Milestone 17). Pure UI: it never
 * touches {@link io.github.jlmc.rikikivault.core.ports.out.GitAuthSettingsPort} itself - the
 * owning {@link SettingsController} hands it the settings to display via {@link #init} and reads
 * back the edited value via {@link #buildSettings()} when the user saves.
 */
public final class GitAuthSettingsPanel {

    @FXML private ToggleGroup authTypeGroup;
    @FXML private RadioButton noneRadio;
    @FXML private RadioButton sshRadio;
    @FXML private RadioButton tokenRadio;
    @FXML private RadioButton httpRadio;
    @FXML private Label activeTypeLabel;
    @FXML private TextField sshKeyPathField;
    @FXML private PasswordField tokenField;
    @FXML private TextField tokenRevealField;
    @FXML private ToggleButton tokenEyeToggle;
    @FXML private TextField httpUsernameField;
    @FXML private PasswordField httpPasswordField;
    @FXML private TextField httpPasswordRevealField;
    @FXML private ToggleButton httpPasswordEyeToggle;

    private Stage ownerStage;
    private Path pendingSshKeyPath;

    void init(Stage ownerStage, GitAuthSettings settings) {
        this.ownerStage = ownerStage;
        pendingSshKeyPath = settings.sshPrivateKeyPath();

        sshKeyPathField.setText(pendingSshKeyPath != null ? pendingSshKeyPath.toString() : "");
        tokenField.setText(settings.githubToken() != null ? settings.githubToken() : "");
        httpUsernameField.setText(settings.httpUsername() != null ? settings.httpUsername() : "");
        httpPasswordField.setText(settings.httpPassword() != null ? settings.httpPassword() : "");

        wireReveal(tokenField, tokenRevealField, tokenEyeToggle);
        wireReveal(httpPasswordField, httpPasswordRevealField, httpPasswordEyeToggle);

        selectRadioFor(settings.activeType());
        authTypeGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> updateActiveTypeLabel());
        updateActiveTypeLabel();
    }

    private static void wireReveal(PasswordField masked, TextField revealed, ToggleButton eyeToggle) {
        revealed.textProperty().bindBidirectional(masked.textProperty());
        revealed.setManaged(false);
        revealed.setVisible(false);
        eyeToggle.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
            revealed.setVisible(isSelected);
            revealed.setManaged(isSelected);
            masked.setVisible(!isSelected);
            masked.setManaged(!isSelected);
        });
    }

    private void selectRadioFor(GitAuthType type) {
        RadioButton radio = switch (type) {
            case SSH -> sshRadio;
            case TOKEN -> tokenRadio;
            case HTTP_BASIC -> httpRadio;
            case NONE -> noneRadio;
        };
        authTypeGroup.selectToggle(radio);
    }

    private GitAuthType selectedType() {
        if (sshRadio.isSelected()) {
            return GitAuthType.SSH;
        }
        if (tokenRadio.isSelected()) {
            return GitAuthType.TOKEN;
        }
        if (httpRadio.isSelected()) {
            return GitAuthType.HTTP_BASIC;
        }
        return GitAuthType.NONE;
    }

    private void updateActiveTypeLabel() {
        String description = switch (selectedType()) {
            case SSH -> Messages.get("gitAuthPanel.type.ssh");
            case TOKEN -> Messages.get("gitAuthPanel.type.token");
            case HTTP_BASIC -> Messages.get("gitAuthPanel.type.http");
            case NONE -> Messages.get("gitAuthPanel.type.none");
        };
        activeTypeLabel.setText(Messages.get("gitAuthPanel.activeType", description));
    }

    @FXML
    private void onChooseSshKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("gitAuthPanel.chooseKeyDialogTitle"));
        File file = chooser.showOpenDialog(ownerStage);
        if (file != null) {
            pendingSshKeyPath = file.toPath();
            sshKeyPathField.setText(pendingSshKeyPath.toString());
        }
    }

    @FXML
    private void onClearSshKey() {
        pendingSshKeyPath = null;
        sshKeyPathField.setText("");
        if (sshRadio.isSelected()) {
            authTypeGroup.selectToggle(noneRadio);
        }
    }

    @FXML
    private void onClearToken() {
        tokenField.clear();
        if (tokenRadio.isSelected()) {
            authTypeGroup.selectToggle(noneRadio);
        }
    }

    @FXML
    private void onClearHttpBasic() {
        httpUsernameField.clear();
        httpPasswordField.clear();
        if (httpRadio.isSelected()) {
            authTypeGroup.selectToggle(noneRadio);
        }
    }

    GitAuthSettings buildSettings() {
        return new GitAuthSettings(
                selectedType(),
                pendingSshKeyPath,
                blankToNull(tokenField.getText()),
                blankToNull(httpUsernameField.getText()),
                blankToNull(httpPasswordField.getText()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
