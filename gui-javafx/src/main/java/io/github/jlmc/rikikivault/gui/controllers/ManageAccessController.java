package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.application.usecase.AuthorizeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.RevokeMachineService;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;
import io.github.jlmc.rikikivault.gui.App;
import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.support.BackgroundTask;
import io.github.jlmc.rikikivault.gui.support.Dialogs;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import io.github.jlmc.rikikivault.gui.support.Messages;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class ManageAccessController {

    private static final String KEY_ALGORITHM = "X25519";

    @FXML private TextField labelField;
    @FXML private TextField publicKeyFileField;
    @FXML private Button authorizeButton;
    @FXML private TextField fingerprintField;
    @FXML private Button revokeButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;

    private Stage stage;
    private VaultContext ctx;
    private Runnable onChanged;
    private Path chosenPublicKeyFile;

    static void open(Stage owner, VaultContext ctx, Runnable onChanged) {
        FXMLLoader loader = Fxml.loader("/fxml/manage-access-view.fxml");
        Parent root = loader.getRoot();
        ManageAccessController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("manageAccess.windowTitle"));
        Scene scene = new Scene(root, 560, 320);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        controller.init(stage, ctx, onChanged);
        stage.showAndWait();
    }

    private void init(Stage stage, VaultContext ctx, Runnable onChanged) {
        this.stage = stage;
        this.ctx = ctx;
        this.onChanged = onChanged;
    }

    @FXML
    private void onChoosePublicKeyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("manageAccess.publicKeyField.prompt"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            chosenPublicKeyFile = file.toPath();
            publicKeyFileField.setText(file.getName());
        }
    }

    @FXML
    private void onAuthorize() {
        String label = labelField.getText();
        if (label == null || label.isBlank()) {
            statusLabel.setText(Messages.get("manageAccess.status.needLabel"));
            return;
        }
        if (chosenPublicKeyFile == null) {
            statusLabel.setText(Messages.get("manageAccess.status.needPublicKeyFile"));
            return;
        }
        PublicKey publicKey = readPublicKeyFile(chosenPublicKeyFile);

        setBusy(true, Messages.get("manageAccess.busy.authorizing"));
        BackgroundTask.run(
                () -> new AuthorizeMachineService(
                        ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                        ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort())
                        .authorize(new AuthorizeMachineCommand(label, publicKey)),
                (Boolean pushed) -> {
                    setBusy(false, "");
                    onChanged.run();
                    stage.close();
                    if (!pushed) {
                        Dialogs.showInfo(Messages.get("manageAccess.authorizedLocally.title"), Messages.get("common.savedLocallyNoRemote"));
                    }
                },
                error -> {
                    setBusy(false, "");
                    Dialogs.showError(error);
                });
    }

    @FXML
    private void onRevoke() {
        String fingerprintHex = fingerprintField.getText();
        if (fingerprintHex == null || fingerprintHex.isBlank()) {
            statusLabel.setText(Messages.get("manageAccess.status.needFingerprint"));
            return;
        }
        KeyFingerprint fingerprint;
        try {
            fingerprint = new KeyFingerprint(fingerprintHex.trim());
        } catch (IllegalArgumentException e) {
            statusLabel.setText(Messages.get("manageAccess.invalidFingerprint", e.getMessage()));
            return;
        }
        if (!Dialogs.confirm(Messages.get("manageAccess.revoke.confirmTitle"), Messages.get("manageAccess.revoke.confirmBody", fingerprint))) {
            return;
        }

        setBusy(true, Messages.get("manageAccess.busy.revoking"));
        BackgroundTask.run(
                () -> new RevokeMachineService(
                        ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                        ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort())
                        .revoke(new RevokeMachineCommand(fingerprint)),
                (Boolean pushed) -> {
                    setBusy(false, "");
                    onChanged.run();
                    stage.close();
                    if (!pushed) {
                        Dialogs.showInfo(Messages.get("manageAccess.revokedLocally.title"), Messages.get("common.savedLocallyNoRemote"));
                    }
                },
                error -> {
                    setBusy(false, "");
                    Dialogs.showError(error);
                });
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisible(busy);
        authorizeButton.setDisable(busy);
        revokeButton.setDisable(busy);
        statusLabel.setText(message);
    }

    private static PublicKey readPublicKeyFile(Path file) {
        try {
            String base64 = Files.readString(file, StandardCharsets.UTF_8).strip();
            byte[] bytes = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (IOException | IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalArgumentException(Messages.get("manageAccess.readKeyFailed", file), e);
        }
    }
}
