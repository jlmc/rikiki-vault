package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalGitAuthSettingsAdapter;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.GitAuthSettingsPort;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Lets the user configure explicit Git authentication (Milestone 13, reworked in Milestone 17):
 * three methods - an SSH private key, a GitHub token (HTTPS), or an HTTP username/password - all
 * storable at once, but only {@link GitAuthSettings#activeType()} is ever actually applied by
 * {@code JGitRepositoryAdapter}. Persisted via {@link GitAuthSettingsPort} (through
 * {@link LocalGitAuthSettingsAdapter}, inheriting that adapter's atomic write and owner-only
 * permissions). Global to the machine, not tied to any open vault, so it's reachable both before
 * one is opened (Welcome screen) and from the main window's toolbar.
 */
public final class GitAuthSettingsController {

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
    @FXML private Label statusLabel;

    private Stage stage;
    private final GitAuthSettingsPort gitAuthSettingsPort = new LocalGitAuthSettingsAdapter(VaultPaths.defaultGitAuthDirectory());
    private Path pendingSshKeyPath;

    static void open(Stage owner) {
        FXMLLoader loader = new FXMLLoader(GitAuthSettingsController.class.getResource("/fxml/git-auth-settings-view.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load git-auth-settings-view.fxml", e);
        }
        GitAuthSettingsController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Definições de Git");
        Scene scene = new Scene(root, 560, 620);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        controller.init(stage);
        stage.showAndWait();
    }

    private void init(Stage stage) {
        this.stage = stage;
        GitAuthSettings settings = gitAuthSettingsPort.load();
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
            case SSH -> "SSH";
            case TOKEN -> "Token GitHub (HTTPS)";
            case HTTP_BASIC -> "Utilizador/Password (HTTP)";
            case NONE -> "Nenhum (descoberta automática)";
        };
        activeTypeLabel.setText("Método ativo: " + description);
    }

    @FXML
    private void onChooseSshKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher chave privada SSH");
        File file = chooser.showOpenDialog(stage);
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

    @FXML
    private void onSave() {
        GitAuthSettings settings = new GitAuthSettings(
                selectedType(),
                pendingSshKeyPath,
                blankToNull(tokenField.getText()),
                blankToNull(httpUsernameField.getText()),
                blankToNull(httpPasswordField.getText()));
        gitAuthSettingsPort.save(settings);
        statusLabel.setText("Guardado.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @FXML
    private void onClose() {
        stage.close();
    }
}
