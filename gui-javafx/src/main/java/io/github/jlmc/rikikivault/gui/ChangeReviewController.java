package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.application.usecase.PublishVaultService;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Review-and-approve step of the "Encrypt &amp; Publish" flow (Plan.md §17): the user can
 * deselect individual changes before publishing - {@link PublishVaultCommand} already accepts any
 * subset of {@link VaultChange}, not just the full scan result.
 */
public final class ChangeReviewController {

    @FXML private TableView<SelectableChange> changesTable;
    @FXML private TableColumn<SelectableChange, Boolean> selectedColumn;
    @FXML private TableColumn<SelectableChange, SelectableChange> statusColumn;
    @FXML private TableColumn<SelectableChange, String> pathColumn;
    @FXML private TextField commitMessageField;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;
    @FXML private Button cancelButton;
    @FXML private Button publishButton;

    private Stage stage;
    private VaultContext ctx;
    private Runnable onPublished;

    static void open(Stage owner, VaultContext ctx, List<VaultChange> changes, Runnable onPublished) {
        FXMLLoader loader = new FXMLLoader(ChangeReviewController.class.getResource("/fxml/change-review-view.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load change-review-view.fxml", e);
        }
        ChangeReviewController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Rever alterações");
        Scene scene = new Scene(root, 620, 480);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        controller.init(stage, ctx, changes, onPublished);
        stage.showAndWait();
    }

    private void init(Stage stage, VaultContext ctx, List<VaultChange> changes, Runnable onPublished) {
        this.stage = stage;
        this.ctx = ctx;
        this.onPublished = onPublished;

        ObservableList<SelectableChange> rows = FXCollections.observableArrayList(
                changes.stream().map(SelectableChange::new).toList());
        changesTable.setItems(rows);
        changesTable.setEditable(true);

        selectedColumn.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectedColumn));
        selectedColumn.setEditable(true);

        statusColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        statusColumn.setCellFactory(column -> new StatusBadgeCell<>(sc -> FileStatus.from(sc.change().type())));

        pathColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().change().path()));
    }

    @FXML
    private void onCancel() {
        stage.close();
    }

    @FXML
    private void onPublish() {
        String commitMessage = commitMessageField.getText();
        if (commitMessage == null || commitMessage.isBlank()) {
            statusLabel.setText("Indica uma mensagem de publicação.");
            return;
        }
        List<VaultChange> selected = changesTable.getItems().stream()
                .filter(SelectableChange::isSelected)
                .map(SelectableChange::change)
                .toList();
        if (selected.isEmpty()) {
            statusLabel.setText("Seleciona pelo menos uma alteração.");
            return;
        }
        if (!Dialogs.confirm("Publicar", "Encriptar e publicar " + selected.size() + " alteração(ões)?")) {
            return;
        }

        setBusy(true, "A publicar...");
        BackgroundTask.run(
                () -> new PublishVaultService(
                        ctx.localFiles(), ctx.documentsFiles(), ctx.encryptionPort(), ctx.hashPort(),
                        ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort())
                        .publish(new PublishVaultCommand(selected, commitMessage)),
                (Boolean pushed) -> {
                    setBusy(false, "");
                    onPublished.run();
                    stage.close();
                    if (!pushed) {
                        Dialogs.showInfo("Publicado localmente", "Guardado localmente - sem remoto configurado.");
                    }
                },
                error -> {
                    setBusy(false, "");
                    Dialogs.showError(error);
                });
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisible(busy);
        publishButton.setDisable(busy);
        cancelButton.setDisable(busy);
        statusLabel.setText(message);
    }
}
