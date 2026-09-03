package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PullVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

public final class MainWindowController {

    @FXML private Label vaultPathLabel;
    @FXML private Label fingerprintLabel;
    @FXML private TableView<FileEntry> fileTable;
    @FXML private TableColumn<FileEntry, FileEntry> statusColumn;
    @FXML private TableColumn<FileEntry, String> pathColumn;
    @FXML private StackPane previewContainer;

    private VaultContext ctx;

    void init(VaultContext ctx) {
        this.ctx = ctx;
        MachineIdentity identity = new LoadMachineIdentityService(ctx.keyStorePort()).load();
        vaultPathLabel.setText(ctx.vaultRoot().toString());
        fingerprintLabel.setText("Identidade: " + identity.id());

        statusColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        statusColumn.setCellFactory(column -> new StatusBadgeCell<>(FileEntry::status));
        pathColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path()));

        fileTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> showPreview(newValue));
        showPreview(null);

        refresh();
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        // Reflects changes made to local/ from outside the app (Finder, another editor, ...).
        // Never touches the remote - pulling still requires the explicit "Pull" action.
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> refresh(false)));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void showPreview(FileEntry entry) {
        if (entry == null) {
            previewContainer.getChildren().setAll(ViewerResultRenderer.renderUnsupported("Seleciona um ficheiro para pré-visualizar."));
            return;
        }
        if (entry.status() == FileStatus.DELETED) {
            previewContainer.getChildren().setAll(
                    ViewerResultRenderer.renderUnsupported("Ficheiro removido - sem conteúdo local para pré-visualizar."));
            return;
        }
        BackgroundTask.run(
                () -> {
                    byte[] content = ctx.localFiles().readFile(entry.path());
                    FileViewer viewer = FileViewerRegistry.select(entry.path());
                    return viewer.view(content, entry.path());
                },
                result -> previewContainer.getChildren().setAll(ViewerResultRenderer.render(result)),
                error -> previewContainer.getChildren().setAll(
                        ViewerResultRenderer.renderUnsupported("Não foi possível pré-visualizar: " + error.getMessage())));
    }

    @FXML
    private void onRefresh() {
        refresh(true);
    }

    @FXML
    private void onPull() {
        BackgroundTask.run(
                () -> new PullVaultService(
                        new LoadMachineIdentityService(ctx.keyStorePort()),
                        new DecryptFileService(ctx.encryptionPort()),
                        new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()),
                        ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.gitRepositoryPort())
                        .pull(),
                (PullResult result) -> {
                    Stage owner = (Stage) fileTable.getScene().getWindow();
                    PullResultController.open(owner, result);
                    refresh();
                },
                Dialogs::showError);
    }

    @FXML
    private void onManageAccess() {
        Stage owner = (Stage) fileTable.getScene().getWindow();
        ManageAccessController.open(owner, ctx, this::refresh);
    }

    @FXML
    private void onPublish() {
        BackgroundTask.run(
                () -> new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()).scan(),
                changes -> {
                    if (changes.isEmpty()) {
                        Dialogs.showInfo("Publicar", "Não há alterações para publicar.");
                        return;
                    }
                    Stage owner = (Stage) fileTable.getScene().getWindow();
                    ChangeReviewController.open(owner, ctx, changes, this::refresh);
                },
                Dialogs::showError);
    }

    private void refresh() {
        refresh(true);
    }

    private void refresh(boolean reportErrors) {
        BackgroundTask.run(
                () -> {
                    List<String> localPaths = ctx.localFiles().listFiles();
                    List<VaultChange> changes = new ScanChangesService(
                            ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()).scan();
                    return FileTreeBuilder.build(localPaths, changes);
                },
                entries -> fileTable.setItems(FXCollections.observableArrayList(entries)),
                error -> {
                    if (reportErrors) {
                        Dialogs.showError(error);
                    }
                });
    }
}
