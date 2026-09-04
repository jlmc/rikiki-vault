package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.support.Messages;
import javafx.fxml.FXML;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.Consumer;

public final class WelcomeController {

    private Stage stage;
    private Consumer<VaultContext> onVaultChosen;

    public void init(Stage stage, Consumer<VaultContext> onVaultChosen) {
        this.stage = stage;
        this.onVaultChosen = onVaultChosen;
    }

    @FXML
    private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Messages.get("welcome.chooseFolderDialogTitle"));
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            onVaultChosen.accept(VaultContext.at(chosen.toPath()));
        }
    }

    @FXML
    private void onOpenSettings() {
        SettingsController.open(stage, () -> {
        });
    }
}
