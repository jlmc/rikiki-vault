package io.github.jlmc.rikikivault.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Read-only modal showing the text a {@code DiffFileUseCase} call produced (Plan.md §16). */
public final class DiffResultController {

    @FXML private TextArea diffArea;

    private Stage stage;

    static void open(Stage owner, String diffText) {
        FXMLLoader loader = new FXMLLoader(DiffResultController.class.getResource("/fxml/diff-result-view.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load diff-result-view.fxml", e);
        }
        DiffResultController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Diff");
        Scene scene = new Scene(root, 640, 480);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        controller.init(stage, diffText);
        stage.showAndWait();
    }

    private void init(Stage stage, String diffText) {
        this.stage = stage;
        diffArea.setText(diffText);
    }

    @FXML
    private void onClose() {
        stage.close();
    }
}
