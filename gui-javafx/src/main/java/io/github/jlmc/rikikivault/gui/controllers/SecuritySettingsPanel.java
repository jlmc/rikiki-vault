package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.domain.exception.RikikiVaultException;
import io.github.jlmc.rikikivault.gui.controls.RevealablePasswordField;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Messages;
import io.github.jlmc.rikikivault.gui.support.Notifications;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The "Segurança" section of the Settings screen: set/change/remove the passphrase protecting
 * this machine's identity. Unlike the Git/Language sections, changes here apply immediately via
 * their own button - never deferred to the shared "Guardar" button - since a passphrase change is
 * a sensitive, one-shot operation on {@code private.key}, not a preference saved to a file.
 *
 * <p>Identity is global to the machine (not tied to any open vault), so this panel builds its own
 * {@link LocalKeyStoreAdapter} straight from {@link VaultPaths#defaultConfigFile()} - exactly like
 * {@code VaultContext.at(...)} does - rather than depending on whichever {@code VaultContext} (if
 * any) happens to be open. If a vault session IS open when a change is applied here, {@link
 * #onPassphraseChanged} lets the host refresh its own cached {@code PassphraseCachingKeyStorePort}
 * so the rest of that session keeps working without asking the user to re-enter it.
 */
public final class SecuritySettingsPanel {

    @FXML private Label statusLabel;
    @FXML private RevealablePasswordField currentField;
    @FXML private RevealablePasswordField newField;
    @FXML private RevealablePasswordField confirmField;
    @FXML private Button applyButton;
    @FXML private Button removeButton;
    @FXML private ProgressIndicator progress;

    private final LocalKeyStoreAdapter keyStorePort = new LocalKeyStoreAdapter(
            new YamlConfigFileAdapter(VaultPaths.defaultConfigFile()).load().identityDirectory());
    private Consumer<char[]> onPassphraseChanged = ignored -> {
    };

    void init(Consumer<char[]> onPassphraseChanged) {
        if (onPassphraseChanged != null) {
            this.onPassphraseChanged = onPassphraseChanged;
        }
        refreshProtectionState();
    }

    private void refreshProtectionState() {
        boolean protectedNow = keyStorePort.isPassphraseProtected();
        statusLabel.setText(Messages.get(protectedNow ? "securityPanel.status.protected" : "securityPanel.status.unprotected"));
        removeButton.setDisable(!protectedNow);
        currentField.clear();
        newField.clear();
        confirmField.clear();
    }

    @FXML
    private void onApply() {
        char[] newPassphrase = newField.getText().toCharArray();
        char[] confirm = confirmField.getText().toCharArray();
        if (newPassphrase.length == 0) {
            statusLabel.setText(Messages.get("passphraseDialog.empty"));
            return;
        }
        if (!Arrays.equals(newPassphrase, confirm)) {
            statusLabel.setText(Messages.get("passphraseDialog.mismatch"));
            return;
        }
        char[] current = keyStorePort.isPassphraseProtected() ? currentField.getText().toCharArray() : null;
        applyChange(current, newPassphrase, Messages.get("passphrase.setSuccess"));
    }

    @FXML
    private void onRemove() {
        char[] current = currentField.getText().toCharArray();
        applyChange(current, null, Messages.get("passphrase.removeSuccess"));
    }

    private void applyChange(char[] current, char[] newOrNull, String successMessage) {
        setBusy(true);
        BackgroundTasks.runVoid(
                () -> keyStorePort.changePassphrase(current, newOrNull),
                () -> {
                    setBusy(false);
                    onPassphraseChanged.accept(newOrNull);
                    Notifications.success(successMessage);
                    refreshProtectionState();
                    wipe(current);
                    wipe(newOrNull);
                },
                error -> {
                    setBusy(false);
                    wipe(current);
                    wipe(newOrNull);
                    Notifications.error(error instanceof RikikiVaultException rve
                            ? rve.getMessage() : Messages.get("errorAdvice.generic.explanation"));
                });
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        applyButton.setDisable(busy);
        removeButton.setDisable(busy || !keyStorePort.isPassphraseProtected());
    }

    private static void wipe(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }

}
