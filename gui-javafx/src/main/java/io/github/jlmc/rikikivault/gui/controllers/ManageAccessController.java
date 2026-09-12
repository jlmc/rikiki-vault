package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.application.usecase.AuthorizeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.RevokeMachineService;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;
import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Dialogs;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import io.github.jlmc.rikikivault.gui.support.Messages;
import io.github.jlmc.rikikivault.gui.support.Notifications;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

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

    private VaultContext ctx;
    private Runnable onChanged;
    private Runnable onClose;
    private Path chosenPublicKeyFile;

    /**
     * Builds the "Gerir Acesso" content as a plain, embeddable {@link Parent} (Milestone 31) -
     * hosted directly by the main window's sidebar panel; no modal window of its own anymore.
     */
    static Parent embed(VaultContext ctx, Runnable onChanged, Runnable onClose) {
        FXMLLoader loader = Fxml.loader("/fxml/manage-access-view.fxml");
        Parent root = loader.getRoot();
        ManageAccessController controller = loader.getController();
        controller.init(ctx, onChanged, onClose);
        return root;
    }

    private void init(VaultContext ctx, Runnable onChanged, Runnable onClose) {
        this.ctx = ctx;
        this.onChanged = onChanged;
        this.onClose = onClose;
    }

    @FXML
    private void onChoosePublicKeyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("manageAccess.publicKeyField.prompt"));
        File file = chooser.showOpenDialog(publicKeyFileField.getScene().getWindow());
        if (file != null) {
            chosenPublicKeyFile = file.toPath();
            publicKeyFileField.setText(file.getName());
        }
    }

    @FXML
    private void onAuthorize() {
        String label = labelField.getText();
        if (label == null || label.isBlank()) {
            Notifications.warning(Messages.get("manageAccess.status.needLabel"));
            return;
        }
        if (chosenPublicKeyFile == null) {
            Notifications.warning(Messages.get("manageAccess.status.needPublicKeyFile"));
            return;
        }
        PublicKey publicKey = readPublicKeyFile(chosenPublicKeyFile);

        setBusy(true);
        BackgroundTasks.run(
                () -> new AuthorizeMachineService(
                        ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                        ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort())
                        .authorize(new AuthorizeMachineCommand(label, publicKey)),
                (Boolean pushed) -> {
                    setBusy(false);
                    onChanged.run();
                    if (onClose != null) {
                        onClose.run();
                    }
                    Notifications.success(pushed
                            ? Messages.get("manageAccess.authorized.pushed")
                            : Messages.get("common.savedLocallyNoRemote"));
                },
                error -> {
                    setBusy(false);
                    Notifications.error(error);
                });
    }

    @FXML
    private void onRevoke() {
        String fingerprintHex = fingerprintField.getText();
        if (fingerprintHex == null || fingerprintHex.isBlank()) {
            Notifications.warning(Messages.get("manageAccess.status.needFingerprint"));
            return;
        }
        KeyFingerprint fingerprint;
        try {
            fingerprint = new KeyFingerprint(fingerprintHex.trim());
        } catch (IllegalArgumentException e) {
            Notifications.warning(Messages.get("manageAccess.invalidFingerprint", e.getMessage()));
            return;
        }
        if (!Dialogs.confirm(Messages.get("manageAccess.revoke.confirmTitle"), Messages.get("manageAccess.revoke.confirmBody", fingerprint))) {
            return;
        }

        setBusy(true);
        BackgroundTasks.run(
                () -> new RevokeMachineService(
                        ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                        ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort())
                        .revoke(new RevokeMachineCommand(fingerprint)),
                (Boolean pushed) -> {
                    setBusy(false);
                    onChanged.run();
                    if (onClose != null) {
                        onClose.run();
                    }
                    Notifications.success(pushed
                            ? Messages.get("manageAccess.revoked.pushed")
                            : Messages.get("common.savedLocallyNoRemote"));
                },
                error -> {
                    setBusy(false);
                    Notifications.error(error);
                });
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        authorizeButton.setDisable(busy);
        revokeButton.setDisable(busy);
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
