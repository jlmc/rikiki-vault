package io.github.jlmc.rikikivault.gui;

import javafx.fxml.FXML;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.Consumer;

public final class WelcomeController {

    private Stage stage;
    private Consumer<VaultContext> onVaultChosen;

    void init(Stage stage, Consumer<VaultContext> onVaultChosen) {
        this.stage = stage;
        this.onVaultChosen = onVaultChosen;
    }

    @FXML
    private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolher pasta do vault");
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            onVaultChosen.accept(VaultContext.at(chosen.toPath()));
        }
    }
}
