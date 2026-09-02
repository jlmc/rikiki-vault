package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PullVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.List;

public final class MainWindowController {

    @FXML private Label vaultPathLabel;
    @FXML private Label fingerprintLabel;
    @FXML private TableView<FileEntry> fileTable;
    @FXML private TableColumn<FileEntry, FileEntry> statusColumn;
    @FXML private TableColumn<FileEntry, String> pathColumn;

    private VaultContext ctx;

    void init(VaultContext ctx) {
        this.ctx = ctx;
        MachineIdentity identity = new LoadMachineIdentityService(ctx.keyStorePort()).load();
        vaultPathLabel.setText(ctx.vaultRoot().toString());
        fingerprintLabel.setText("Identidade: " + identity.id());

        statusColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        statusColumn.setCellFactory(column -> new StatusBadgeCell<>(FileEntry::status));
        pathColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path()));

        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
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
        BackgroundTask.run(
                () -> {
                    List<String> localPaths = ctx.localFiles().listFiles();
                    List<VaultChange> changes = new ScanChangesService(
                            ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()).scan();
                    return FileTreeBuilder.build(localPaths, changes);
                },
                entries -> fileTable.setItems(FXCollections.observableArrayList(entries)),
                Dialogs::showError);
    }
}
