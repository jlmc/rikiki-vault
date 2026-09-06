package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.adapters.keystore.PassphraseCachingKeyStorePort;
import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import org.kordamp.ikonli.javafx.FontIcon;

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
    @FXML private PasswordField passphraseField;
    @FXML private TextField passphraseRevealField;
    @FXML private ToggleButton eyeToggle;
    @FXML private FontIcon eyeIcon;
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
        wireReveal(passphraseField, passphraseRevealField, eyeToggle, eyeIcon);
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

    private static void wireReveal(PasswordField masked, TextField revealed, ToggleButton eyeToggle, FontIcon eyeIcon) {
        revealed.textProperty().bindBidirectional(masked.textProperty());
        revealed.setManaged(false);
        revealed.setVisible(false);
        eyeToggle.selectedProperty().addListener((_, _, isSelected) -> {
            revealed.setVisible(isSelected);
            revealed.setManaged(isSelected);
            masked.setVisible(!isSelected);
            masked.setManaged(!isSelected);
            eyeIcon.setIconLiteral(isSelected ? "fth-eye-off" : "fth-eye");
        });
    }
}
