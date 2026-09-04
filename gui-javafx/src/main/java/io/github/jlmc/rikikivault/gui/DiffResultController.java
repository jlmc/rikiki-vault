package io.github.jlmc.rikikivault.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Read-only modal showing the text a {@code DiffFileUseCase} call produced (Plan.md §16). */
public final class DiffResultController {

    @FXML private TextArea diffArea;

    private Stage stage;

    static void open(Stage owner, String diffText) {
        FXMLLoader loader = Fxml.loader("/fxml/diff-result-view.fxml");
        Parent root = loader.getRoot();
        DiffResultController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("mainWindow.editor.diff"));
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
