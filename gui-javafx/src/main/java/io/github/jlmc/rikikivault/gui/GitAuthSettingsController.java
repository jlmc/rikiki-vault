package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalGitAuthSettingsAdapter;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.GitAuthSettingsPort;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Lets the user configure explicit Git authentication (Milestone 13): an SSH private key path
 * that overrides implicit discovery, and/or an HTTPS token - stored via {@link GitAuthSettingsPort}
 * (which persists through {@link LocalGitAuthSettingsAdapter}, inheriting that adapter's atomic
 * write and owner-only permissions). Global to the machine, not tied to any open vault, so it's
 * reachable both before one is opened (Welcome screen) and from the main window's toolbar.
 */
public final class GitAuthSettingsController {

    @FXML private TextField sshKeyPathField;
    @FXML private Label tokenStatusLabel;
    @FXML private PasswordField tokenField;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;

    private Stage stage;
    private final GitAuthSettingsPort gitAuthSettingsPort = new LocalGitAuthSettingsAdapter(VaultPaths.defaultGitAuthDirectory());
    private GitAuthSettings currentSettings;
    private Path pendingSshKeyPath;
    private boolean tokenCleared;

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
        Scene scene = new Scene(root, 560, 320);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        controller.init(stage);
        stage.showAndWait();
    }

    private void init(Stage stage) {
        this.stage = stage;
        this.currentSettings = gitAuthSettingsPort.load();
        this.pendingSshKeyPath = currentSettings.sshPrivateKeyPath();
        refreshFields();
    }

    private void refreshFields() {
        sshKeyPathField.setText(pendingSshKeyPath != null ? pendingSshKeyPath.toString() : "");
        boolean hasToken = !tokenCleared && currentSettings.githubToken() != null && !currentSettings.githubToken().isBlank();
        tokenStatusLabel.setText(hasToken ? "Token: configurado" : "Token: não configurado");
    }

    @FXML
    private void onChooseSshKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher chave privada SSH");
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            pendingSshKeyPath = file.toPath();
            refreshFields();
        }
    }

    @FXML
    private void onClearSshKey() {
        pendingSshKeyPath = null;
        refreshFields();
    }

    @FXML
    private void onClearToken() {
        tokenCleared = true;
        tokenField.clear();
        refreshFields();
    }

    @FXML
    private void onSave() {
        String typedToken = tokenField.getText();
        String newToken;
        if (typedToken != null && !typedToken.isBlank()) {
            newToken = typedToken;
        } else if (tokenCleared) {
            newToken = null;
        } else {
            newToken = currentSettings.githubToken();
        }
        gitAuthSettingsPort.save(new GitAuthSettings(pendingSshKeyPath, newToken));
        statusLabel.setText("Guardado.");
    }

    @FXML
    private void onClose() {
        stage.close();
    }
}
