package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultConflict;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Shows what a {@code pull} changed (Plan.md §11) and, for conflicts, reports them (§32) without
 * offering "Keep Remote"/"Compare" - those need a use case that doesn't exist yet to force a
 * remote overwrite; "Keep Local" is already what {@link io.github.jlmc.rikikivault.core.application.usecase.PullVaultService}
 * does automatically, so there's nothing to click for it.
 */
public final class PullResultController {

    @FXML private Label emptyLabel;
    @FXML private VBox uncommittedSection;
    @FXML private ListView<String> uncommittedList;
    @FXML private VBox updatedSection;
    @FXML private ListView<String> updatedList;
    @FXML private VBox deletedSection;
    @FXML private ListView<String> deletedList;
    @FXML private VBox conflictsSection;
    @FXML private ListView<String> conflictsList;

    private Stage stage;

    static void open(Stage owner, PullResult result) {
        FXMLLoader loader = new FXMLLoader(PullResultController.class.getResource("/fxml/pull-result-view.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load pull-result-view.fxml", e);
        }
        PullResultController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Pull");
        Scene scene = new Scene(root, 480, 520);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        controller.init(stage, result);
        stage.showAndWait();
    }

    private void init(Stage stage, PullResult result) {
        this.stage = stage;

        fillOrHide(uncommittedSection, uncommittedList,
                result.uncommittedLocalChangesAtStart().stream().map(this::describe).toList());
        fillOrHide(updatedSection, updatedList, result.updatedPaths());
        fillOrHide(deletedSection, deletedList, result.deletedPaths());
        fillOrHide(conflictsSection, conflictsList,
                result.conflicts().stream().map(this::describe).toList());

        boolean nothingHappened = result.updatedPaths().isEmpty() && result.deletedPaths().isEmpty() && !result.hasConflicts();
        emptyLabel.setManaged(nothingHappened);
        emptyLabel.setVisible(nothingHappened);
    }

    private void fillOrHide(VBox section, ListView<String> listView, java.util.List<String> items) {
        listView.setItems(FXCollections.observableArrayList(items));
        section.setManaged(!items.isEmpty());
        section.setVisible(!items.isEmpty());
    }

    private String describe(VaultChange change) {
        return change.type() + "  " + change.path();
    }

    private String describe(VaultConflict conflict) {
        StringBuilder text = new StringBuilder(conflict.plaintextPath())
                .append("  (local: ").append(conflict.localChangeType())
                .append(", remoto: ").append(conflict.remoteChangeType()).append(")");
        if (conflict.localHash() != null) {
            text.append("\n  Local SHA-256: ").append(conflict.localHash());
        }
        if (conflict.remoteHash() != null) {
            text.append("\n  Remoto SHA-256: ").append(conflict.remoteHash());
        }
        return text.toString();
    }

    @FXML
    private void onClose() {
        stage.close();
    }
}
