package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.CloneVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

import java.util.function.Consumer;

public final class InitOrCloneController {

    @FXML private Label titleLabel;
    @FXML private TextField labelField;
    @FXML private CheckBox gitCheckBox;
    @FXML private Button initButton;
    @FXML private TextField remoteField;
    @FXML private Button cloneButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;

    private VaultContext ctx;
    private Consumer<VaultContext> onReady;

    void init(VaultContext ctx, Consumer<VaultContext> onReady) {
        this.ctx = ctx;
        this.onReady = onReady;
        titleLabel.setText("Pasta ainda não é um vault: " + ctx.vaultRoot());
    }

    @FXML
    private void onInitialize() {
        String machineLabel = labelField.getText();
        if (machineLabel == null || machineLabel.isBlank()) {
            statusLabel.setText("Indica um nome para esta máquina.");
            return;
        }
        setBusy(initButton, "A inicializar...");
        boolean initGit = gitCheckBox.isSelected();
        BackgroundTask.runVoid(
                () -> new InitializeVaultService(
                        new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                        new LocalFileSystemAdapter(ctx.vaultRoot()),
                        ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort())
                        .initialize(new InitializeVaultCommand(initGit, machineLabel)),
                () -> {
                    clearBusy();
                    onReady.accept(ctx);
                },
                error -> {
                    clearBusy();
                    Dialogs.showError(error);
                });
    }

    @FXML
    private void onClone() {
        String remoteUri = remoteField.getText();
        if (remoteUri == null || remoteUri.isBlank()) {
            statusLabel.setText("Indica o URL do repositório remoto.");
            return;
        }
        setBusy(cloneButton, "A clonar...");
        JceHybridEncryptionAdapter encryptionPort = ctx.encryptionPort();
        BackgroundTask.runVoid(
                () -> new CloneVaultService(
                        new LoadMachineIdentityService(ctx.keyStorePort()),
                        new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                        new DecryptFileService(encryptionPort),
                        ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.gitRepositoryPort())
                        .clone(new CloneVaultCommand(remoteUri)),
                () -> {
                    clearBusy();
                    onReady.accept(ctx);
                },
                error -> {
                    clearBusy();
                    Dialogs.showError(error);
                });
    }

    private void setBusy(Button button, String message) {
        progress.setVisible(true);
        button.setDisable(true);
        statusLabel.setText(message);
    }

    private void clearBusy() {
        progress.setVisible(false);
        initButton.setDisable(false);
        cloneButton.setDisable(false);
        statusLabel.setText("");
    }
}
