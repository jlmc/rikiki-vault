package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultConflict;
import io.github.jlmc.rikikivault.gui.App;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import io.github.jlmc.rikikivault.gui.support.Messages;
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
        FXMLLoader loader = Fxml.loader("/fxml/pull-result-view.fxml");
        Parent root = loader.getRoot();
        PullResultController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("pullResult.windowTitle"));
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
                .append("  (").append(Messages.get("pullResult.conflict.local")).append(": ").append(conflict.localChangeType())
                .append(", ").append(Messages.get("pullResult.conflict.remote")).append(": ").append(conflict.remoteChangeType())
                .append(")");
        if (conflict.localHash() != null) {
            text.append("\n  ").append(Messages.get("pullResult.conflict.localHash")).append(": ").append(conflict.localHash());
        }
        if (conflict.remoteHash() != null) {
            text.append("\n  ").append(Messages.get("pullResult.conflict.remoteHash")).append(": ").append(conflict.remoteHash());
        }
        return text.toString();
    }

    @FXML
    private void onClose() {
        stage.close();
    }
}
