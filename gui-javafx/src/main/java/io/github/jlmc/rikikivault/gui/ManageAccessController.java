package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.application.usecase.AuthorizeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.RevokeMachineService;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;
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
import java.io.UncheckedIOException;
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
        FXMLLoader loader = new FXMLLoader(ManageAccessController.class.getResource("/fxml/manage-access-view.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load manage-access-view.fxml", e);
        }
        ManageAccessController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Gerir acesso");
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
        chooser.setTitle("Escolher ficheiro .pub");
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
            statusLabel.setText("Indica um nome para a máquina.");
            return;
        }
        if (chosenPublicKeyFile == null) {
            statusLabel.setText("Escolhe o ficheiro .pub da máquina.");
            return;
        }
        PublicKey publicKey = readPublicKeyFile(chosenPublicKeyFile);

        setBusy(true, "A autorizar...");
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
                        Dialogs.showInfo("Autorizado localmente", "Guardado localmente - sem remoto configurado.");
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
            statusLabel.setText("Indica o fingerprint da máquina a revogar.");
            return;
        }
        KeyFingerprint fingerprint;
        try {
            fingerprint = new KeyFingerprint(fingerprintHex.trim());
        } catch (IllegalArgumentException e) {
            statusLabel.setText("Fingerprint inválido: " + e.getMessage());
            return;
        }
        if (!Dialogs.confirm("Revogar", "Revogar o acesso da máquina " + fingerprint + "?")) {
            return;
        }

        setBusy(true, "A revogar...");
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
                        Dialogs.showInfo("Revogado localmente", "Guardado localmente - sem remoto configurado.");
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
            throw new IllegalArgumentException("Não foi possível ler a chave pública de " + file, e);
        }
    }
}
