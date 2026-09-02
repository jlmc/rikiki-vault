package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

public final class MainWindowController {

    private static final List<String> STATUS_STYLE_CLASSES = List.of(
            FileStatus.SYNCED.styleClass(), FileStatus.ADDED.styleClass(),
            FileStatus.MODIFIED.styleClass(), FileStatus.DELETED.styleClass());

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
        statusColumn.setCellFactory(column -> new StatusBadgeCell());
        pathColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path()));

        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
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

    private static final class StatusBadgeCell extends TableCell<FileEntry, FileEntry> {
        @Override
        protected void updateItem(FileEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll(STATUS_STYLE_CLASSES);
            getStyleClass().add("status-badge");
            if (empty || entry == null) {
                setText(null);
            } else {
                setText(entry.status().symbol());
                getStyleClass().add(entry.status().styleClass());
            }
        }
    }
}
