package io.github.jlmc.rikikivault.gui.controllers;

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
import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Dialogs;
import io.github.jlmc.rikikivault.gui.support.Messages;
import io.github.jlmc.rikikivault.gui.support.Notifications;
import io.github.jlmc.rikikivault.gui.support.PassphraseDialogs;
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
    @FXML private TextField initRemoteUrlField;
    @FXML private Button initButton;
    @FXML private TextField remoteField;
    @FXML private Button cloneButton;
    @FXML private ProgressIndicator progress;

    private VaultContext ctx;
    private Consumer<VaultContext> onReady;

    public void init(VaultContext ctx, Consumer<VaultContext> onReady) {
        this.ctx = ctx;
        this.onReady = onReady;
        titleLabel.setText(Messages.get("initOrClone.titleLabel", ctx.vaultRoot()));
        initRemoteUrlField.disableProperty().bind(gitCheckBox.selectedProperty().not());
    }

    @FXML
    private void onInitialize() {
        String machineLabel = labelField.getText();
        if (machineLabel == null || machineLabel.isBlank()) {
            Notifications.warning(Messages.get("initOrClone.status.needMachineLabel"));
            return;
        }
        setBusy(initButton);
        boolean initGit = gitCheckBox.isSelected();
        String remoteUri = initRemoteUrlField.getText();
        boolean hadNoIdentityBefore = !ctx.keyStorePort().exists();
        BackgroundTasks.runVoid(
                () -> new InitializeVaultService(
                        new LoadMachineIdentityService(ctx.keyStorePort()),
                        new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                        new LocalFileSystemAdapter(ctx.vaultRoot()),
                        ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort())
                        .initialize(new InitializeVaultCommand(initGit, machineLabel, remoteUri)),
                () -> {
                    clearBusy();
                    if (hadNoIdentityBefore) {
                        offerPassphraseAtCreation();
                    }
                    Notifications.success(Messages.get("initOrClone.initialized.success"));
                    onReady.accept(ctx);
                },
                error -> {
                    clearBusy();
                    Notifications.error(error);
                });
    }

    @FXML
    private void onClone() {
        String remoteUri = remoteField.getText();
        if (remoteUri == null || remoteUri.isBlank()) {
            Notifications.warning(Messages.get("initOrClone.status.needRemoteUrl"));
            return;
        }
        setBusy(cloneButton);
        JceHybridEncryptionAdapter encryptionPort = ctx.encryptionPort();
        boolean hadNoIdentityBefore = !ctx.keyStorePort().exists();
        BackgroundTasks.runVoid(
                () -> new CloneVaultService(
                        new LoadMachineIdentityService(ctx.keyStorePort()),
                        new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                        new DecryptFileService(encryptionPort),
                        ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort())
                        .clone(new CloneVaultCommand(remoteUri)),
                () -> {
                    clearBusy();
                    if (hadNoIdentityBefore) {
                        offerPassphraseAtCreation();
                    }
                    Notifications.success(Messages.get("initOrClone.cloned.success"));
                    onReady.accept(ctx);
                },
                error -> {
                    clearBusy();
                    Notifications.error(error);
                });
    }

    /**
     * Offered once, right after a brand-new machine identity is generated - never re-asked for an
     * identity that already existed before this init/clone ran.
     */
    private void offerPassphraseAtCreation() {
        if (!Dialogs.confirm(Messages.get("passphraseOffer.title"), Messages.get("passphraseOffer.body"))) {
            return;
        }
        PassphraseDialogs.promptNewPassphrase().ifPresent(newPassphrase ->
                BackgroundTasks.runVoid(
                        () -> ctx.keyStorePort().changePassphrase(null, newPassphrase),
                        () -> {
                            java.util.Arrays.fill(newPassphrase, '\0');
                            Notifications.success(Messages.get("passphrase.setSuccess"));
                        },
                        error -> {
                            java.util.Arrays.fill(newPassphrase, '\0');
                            Notifications.error(error);
                        }));
    }

    private void setBusy(Button button) {
        progress.setVisible(true);
        button.setDisable(true);
    }

    private void clearBusy() {
        progress.setVisible(false);
        initButton.setDisable(false);
        cloneButton.setDisable(false);
    }
}
