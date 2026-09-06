package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.adapters.keystore.PassphraseCachingKeyStorePort;
import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.controls.RevealablePasswordField;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Full-screen "enter the passphrase to unlock this machine's identity" prompt, shown once by
 * {@code App.openVault} before either InitOrClone or the main window is ever reached. The PBKDF2
 * check ({@code KeyStorePort.load(char[])}) always runs via {@link BackgroundTasks} - it's
 * deliberately slow (that's the point of the KDF), so it must never block the FX thread.
 */
public final class PassphrasePromptController {

    @FXML private Label titleLabel;
    @FXML private RevealablePasswordField passphraseField;
    @FXML private Button unlockButton;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label errorLabel;

    private VaultContext ctx;
    private Consumer<VaultContext> onUnlocked;
    private Runnable onCancel;

    public void init(VaultContext ctx, Consumer<VaultContext> onUnlocked, Runnable onCancel) {
        this.ctx = ctx;
        this.onUnlocked = onUnlocked;
        this.onCancel = onCancel;
        titleLabel.setText(Messages.get("passphrasePrompt.title", ctx.vaultRoot()));
    }

    @FXML
    private void onUnlock() {
        char[] passphrase = passphraseField.getText().toCharArray();
        if (passphrase.length == 0) {
            return;
        }
        setBusy(true);
        BackgroundTasks.run(
                () -> ctx.keyStorePort().load(passphrase),
                identity -> {
                    setBusy(false);
                    VaultContext unlocked = ctx.withKeyStorePort(new PassphraseCachingKeyStorePort(ctx.keyStorePort(), passphrase));
                    onUnlocked.accept(unlocked);
                },
                error -> {
                    setBusy(false);
                    Arrays.fill(passphrase, '\0');
                    passphraseField.clear();
                    errorLabel.setText(Messages.get("passphrasePrompt.wrongPassphrase"));
                    errorLabel.setVisible(true);
                    errorLabel.setManaged(true);
                });
    }

    @FXML
    private void onCancelAction() {
        onCancel.run();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        unlockButton.setDisable(busy);
        cancelButton.setDisable(busy);
    }
}
