package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.application.usecase.PublishVaultService;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import io.github.jlmc.rikikivault.gui.App;
import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.filetree.FileStatus;
import io.github.jlmc.rikikivault.gui.filetree.SelectableChange;
import io.github.jlmc.rikikivault.gui.filetree.StatusBadgeCell;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Dialogs;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import io.github.jlmc.rikikivault.gui.support.Messages;
import io.github.jlmc.rikikivault.gui.support.Notifications;
import io.github.jlmc.rikikivault.gui.support.RemotePush;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * Review-and-approve step of the "Encrypt &amp; Publish" flow: the user can
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
    @FXML private Button cancelButton;
    @FXML private Button publishButton;

    private Stage stage;
    private VaultContext ctx;
    private Runnable onPublished;

    static void open(Stage owner, VaultContext ctx, List<VaultChange> changes, Runnable onPublished) {
        FXMLLoader loader = Fxml.loader("/fxml/change-review-view.fxml");
        Parent root = loader.getRoot();
        ChangeReviewController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("changeReview.windowTitle"));
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
            Notifications.warning(Messages.get("changeReview.status.needMessage"));
            return;
        }
        List<VaultChange> selected = changesTable.getItems().stream()
                .filter(SelectableChange::isSelected)
                .map(SelectableChange::change)
                .toList();
        if (selected.isEmpty()) {
            Notifications.warning(Messages.get("changeReview.status.needSelection"));
            return;
        }
        if (!Dialogs.confirm(Messages.get("mainWindow.publish.title"), Messages.get("changeReview.confirmBody", selected.size()))) {
            return;
        }

        PublishVaultService service = new PublishVaultService(
                ctx.localFiles(), ctx.documentsFiles(), ctx.encryptionPort(),
                ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort());

        // Phase 1 - local only. Must never fail because of the remote; a failure here means
        // nothing was actually saved, so it's a genuine blocking error.
        setBusy(true);
        BackgroundTasks.runVoid(
                () -> service.publishLocally(new PublishVaultCommand(selected, commitMessage)),
                this::onLocalPublishSucceeded,
                error -> {
                    setBusy(false);
                    Notifications.error(error);
                });
    }

    // Phase 2 - remote, optional. The local commit from phase 1 already succeeded by this point,
    // so nothing here is ever reported as a blocking "Erro" - at worst a warning that the push
    // itself didn't happen.
    private void onLocalPublishSucceeded() {
        BackgroundTasks.run(
                () -> ctx.gitRepositoryPort().hasRemote(),
                hasRemote -> {
                    if (!hasRemote) {
                        finishPublish();
                        Notifications.success(Messages.get("common.savedLocallyNoRemote"));
                        return;
                    }
                    setBusy(false);
                    if (!Dialogs.confirm(Messages.get("mainWindow.publish.remoteTitle"), Messages.get("changeReview.confirmRemoteBody"))) {
                        finishPublish();
                        Notifications.success(Messages.get("changeReview.savedLocallyLater"));
                        return;
                    }
                    setBusy(true);
                    RemotePush.pushInBackground(ctx.gitRepositoryPort(), this::finishPublish);
                },
                error -> {
                    finishPublish();
                    Notifications.warning(Messages.get("changeReview.remoteCheckFailed", Dialogs.fullMessage(error)));
                });
    }

    private void finishPublish() {
        setBusy(false);
        onPublished.run();
        stage.close();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        publishButton.setDisable(busy);
        cancelButton.setDisable(busy);
    }
}
