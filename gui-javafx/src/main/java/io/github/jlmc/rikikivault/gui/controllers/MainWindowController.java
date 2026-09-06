package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.adapters.diff.TextDiffAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.ClearLocalFilesService;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.DiffFileService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PullVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.RestoreLocalFilesService;
import io.github.jlmc.rikikivault.core.application.usecase.RevertFileService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.model.ClearLocalFilesResult;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.RemoteSyncStatus;
import io.github.jlmc.rikikivault.core.domain.model.RestoreLocalFilesResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.ClearLocalFilesCommand;
import io.github.jlmc.rikikivault.core.ports.in.DiffFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.RestoreLocalFilesCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevertFileCommand;
import io.github.jlmc.rikikivault.gui.VaultContext;
import io.github.jlmc.rikikivault.gui.filetree.FileEntry;
import io.github.jlmc.rikikivault.gui.filetree.FileStatus;
import io.github.jlmc.rikikivault.gui.filetree.FileTreeBuilder;
import io.github.jlmc.rikikivault.gui.filetree.FolderTreeBuilder;
import io.github.jlmc.rikikivault.gui.filetree.FolderTreeNode;
import io.github.jlmc.rikikivault.gui.filetree.StatusBadgeTreeCell;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Dialogs;
import io.github.jlmc.rikikivault.gui.support.Messages;
import io.github.jlmc.rikikivault.gui.support.RemotePush;
import io.github.jlmc.rikikivault.gui.viewer.FileViewer;
import io.github.jlmc.rikikivault.gui.viewer.FileViewerRegistry;
import io.github.jlmc.rikikivault.gui.viewer.TextFileViewer;
import io.github.jlmc.rikikivault.gui.viewer.ViewerResult;
import io.github.jlmc.rikikivault.gui.viewer.ViewerResultRenderer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MainWindowController {

    private static final Logger log = LoggerFactory.getLogger(MainWindowController.class);

    @FXML private Label vaultPathLabel;
    @FXML private Label fingerprintLabel;
    @FXML private Label remoteSyncLabel;
    @FXML private TreeTableView<FolderTreeNode> fileTable;
    @FXML private TreeTableColumn<FolderTreeNode, String> nameColumn;
    @FXML private TreeTableColumn<FolderTreeNode, FolderTreeNode> statusColumn;
    @FXML private StackPane previewContainer;
    @FXML private Button editToggleButton;
    @FXML private Button saveButton;
    @FXML private Button encryptButton;
    @FXML private Button revertButton;
    @FXML private Button diffButton;
    @FXML private Label emptyStateLabel;
    @FXML private VBox navRail;
    @FXML private Button railToggleButton;
    @FXML private ToggleButton filesNavButton;
    @FXML private ToggleButton settingsNavButton;
    @FXML private ToggleButton manageAccessNavButton;
    @FXML private StackPane centerContainer;
    @FXML private SplitPane filesView;

    private VaultContext ctx;
    private FolderTreeNode currentNode;
    private boolean editMode;
    private TextArea editorArea;
    private Runnable onLanguageChanged;
    private MainView currentView = MainView.FILES;
    private boolean railExpanded = true;

    private enum MainView { FILES, SETTINGS, MANAGE_ACCESS }

    public void init(VaultContext ctx, Runnable onLanguageChanged) {
        this.ctx = ctx;
        this.onLanguageChanged = onLanguageChanged;
        MachineIdentity identity = loadOrCreateIdentity();
        vaultPathLabel.setText(ctx.vaultRoot().toString());
        fingerprintLabel.setText(Messages.get("mainWindow.identity", identity.id()));

        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getValue().name()));
        statusColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getValue()));
        statusColumn.setCellFactory(column -> new StatusBadgeTreeCell<>(
                node -> node.fileEntry() == null ? null : node.fileEntry().status()));

        fileTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> showPreview(newValue == null ? null : newValue.getValue()));
        showPreview(null);

        refresh();
        refreshRemoteSyncStatus();
        startAutoRefresh();
    }

    private MachineIdentity loadOrCreateIdentity() {
        try {
            return new LoadMachineIdentityService(ctx.keyStorePort()).load();
        } catch (PrivateKeyNotFoundException e) {
            MachineIdentity identity = new InitializeMachineIdentityService(
                    new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()).initialize();
            Dialogs.showInfo(Messages.get("mainWindow.newIdentity.title"), Messages.get("mainWindow.newIdentity.body"));
            return identity;
        }
    }

    private void startAutoRefresh() {
        // Reflects changes made to local/ from outside the app (Finder, another editor, ...).
        // Never touches the remote - pulling still requires the explicit "Pull" action.
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> refresh(false)));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void showPreview(FolderTreeNode node) {
        if (editMode) {
            // fileTable.setRoot(...) (refresh) clears the selection for a moment before the
            // matching item is reselected on the rebuilt tree - that transient null, and a
            // reselect of the very file being edited, must never bounce us out of edit mode.
            // Only a genuine switch to a *different* file should do that.
            String newPath = node != null && !node.isFolder() ? node.fileEntry().path() : null;
            String oldPath = currentNode != null && !currentNode.isFolder() ? currentNode.fileEntry().path() : null;
            if (newPath == null || newPath.equals(oldPath)) {
                if (node != null) {
                    currentNode = node;
                }
                return;
            }
        }
        currentNode = node;
        exitEditMode();

        if (node == null) {
            editToggleButton.setDisable(true);
            previewContainer.getChildren().setAll(ViewerResultRenderer.renderUnsupported(Messages.get("mainWindow.preview.selectFile")));
            return;
        }
        if (node.isFolder()) {
            editToggleButton.setDisable(true);
            previewContainer.getChildren().setAll(ViewerResultRenderer.renderUnsupported(Messages.get("mainWindow.preview.selectFolder")));
            return;
        }
        FileEntry entry = node.fileEntry();
        if (entry.status() == FileStatus.DELETED) {
            editToggleButton.setDisable(true);
            previewContainer.getChildren().setAll(
                    ViewerResultRenderer.renderUnsupported(Messages.get("mainWindow.preview.deletedFile")));
            return;
        }
        BackgroundTasks.run(
                () -> {
                    byte[] content = ctx.localFiles().readFile(entry.path());
                    FileViewer viewer = FileViewerRegistry.select(entry.path());
                    ViewerResult result = viewer.view(content, entry.path());
                    boolean editable = TextFileViewer.tryDecodeUtf8(content) != null;
                    return new PreviewLoad(result, editable);
                },
                load -> {
                    editToggleButton.setDisable(!load.editable());
                    previewContainer.getChildren().setAll(ViewerResultRenderer.render(load.result()));
                },
                error -> {
                    editToggleButton.setDisable(true);
                    previewContainer.getChildren().setAll(
                            ViewerResultRenderer.renderUnsupported(Messages.get("mainWindow.preview.error", error.getMessage())));
                });
    }

    private record PreviewLoad(ViewerResult result, boolean editable) {
    }

    @FXML
    private void onToggleEdit() {
        if (editMode) {
            exitEditMode();
            showPreview(currentNode);
        } else {
            enterEditMode();
        }
    }

    private void enterEditMode() {
        if (currentNode == null || currentNode.isFolder()) {
            return;
        }
        String path = currentNode.fileEntry().path();
        BackgroundTasks.run(
                () -> ctx.localFiles().readFile(path),
                bytes -> {
                    editorArea = new TextArea(TextFileViewer.tryDecodeUtf8(bytes));
                    editorArea.setWrapText(false);
                    editorArea.getStyleClass().add("preview-text");
                    previewContainer.getChildren().setAll(editorArea);
                    editMode = true;
                    editToggleButton.setText(Messages.get("mainWindow.editor.view"));
                    setEditActionButtonsVisible(true);
                },
                Dialogs::showError);
    }

    private void exitEditMode() {
        editMode = false;
        editorArea = null;
        editToggleButton.setText(Messages.get("mainWindow.editor.edit"));
        setEditActionButtonsVisible(false);
    }

    private void setEditActionButtonsVisible(boolean visible) {
        saveButton.setVisible(visible);
        saveButton.setManaged(visible);
        encryptButton.setVisible(visible);
        encryptButton.setManaged(visible);
        revertButton.setVisible(visible);
        revertButton.setManaged(visible);
        diffButton.setVisible(visible);
        diffButton.setManaged(visible);
    }

    @FXML
    private void onSave() {
        if (currentNode == null || currentNode.isFolder() || editorArea == null) {
            return;
        }
        String path = currentNode.fileEntry().path();
        String text = editorArea.getText();
        BackgroundTasks.runVoid(
                () -> ctx.localFiles().writeFile(path, text.getBytes(StandardCharsets.UTF_8)),
                () -> refresh(true),
                Dialogs::showError);
    }

    @FXML
    private void onEncrypt() {
        if (editorArea == null || currentNode == null || currentNode.isFolder()) {
            onPublish();
            return;
        }
        String path = currentNode.fileEntry().path();
        String text = editorArea.getText();
        BackgroundTasks.runVoid(
                () -> ctx.localFiles().writeFile(path, text.getBytes(StandardCharsets.UTF_8)),
                this::onPublish,
                Dialogs::showError);
    }

    @FXML
    private void onRevert() {
        if (currentNode == null || currentNode.isFolder()) {
            return;
        }
        String path = currentNode.fileEntry().path();
        if (!Dialogs.confirm(Messages.get("mainWindow.revert.title"), Messages.get("mainWindow.revert.confirm", path))) {
            return;
        }
        BackgroundTasks.runVoid(
                () -> new RevertFileService(
                        ctx.manifestPort(), ctx.localFiles(), ctx.documentsFiles(),
                        new DecryptFileService(ctx.encryptionPort()), new LoadMachineIdentityService(ctx.keyStorePort()))
                        .revert(new RevertFileCommand(path)),
                () -> reloadEditorContent(path),
                Dialogs::showError);
    }

    private void reloadEditorContent(String path) {
        BackgroundTasks.run(
                () -> ctx.localFiles().readFile(path),
                bytes -> {
                    if (editorArea != null) {
                        editorArea.setText(TextFileViewer.tryDecodeUtf8(bytes));
                    }
                    refresh(true);
                },
                Dialogs::showError);
    }

    @FXML
    private void onDiff() {
        if (currentNode == null || currentNode.isFolder() || editorArea == null) {
            return;
        }
        String path = currentNode.fileEntry().path();
        byte[] currentContent = editorArea.getText().getBytes(StandardCharsets.UTF_8);
        BackgroundTasks.run(
                () -> new DiffFileService(
                        ctx.manifestPort(), ctx.documentsFiles(),
                        new DecryptFileService(ctx.encryptionPort()), new LoadMachineIdentityService(ctx.keyStorePort()),
                        new TextDiffAdapter())
                        .diff(new DiffFileCommand(path, currentContent)),
                diffText -> {
                    // vaultPathLabel lives in the top toolbar, which (unlike fileTable) is never
                    // detached from the scene when the rail switches away from the Files view.
                    Stage owner = (Stage) vaultPathLabel.getScene().getWindow();
                    DiffResultController.open(owner, diffText);
                },
                Dialogs::showError);
    }

    @FXML
    private void onRefresh() {
        refresh(true);
        refreshRemoteSyncStatus();
    }

    @FXML
    private void onPull() {
        log.info("User triggered Pull");
        BackgroundTasks.run(
                () -> new PullVaultService(
                        new LoadMachineIdentityService(ctx.keyStorePort()),
                        new DecryptFileService(ctx.encryptionPort()),
                        new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()),
                        ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.gitRepositoryPort(), ctx.hashPort())
                        .pull(),
                (PullResult result) -> {
                    // vaultPathLabel lives in the top toolbar, which (unlike fileTable) is never
                    // detached from the scene when the rail switches away from the Files view.
                    Stage owner = (Stage) vaultPathLabel.getScene().getWindow();
                    PullResultController.open(owner, result);
                    refresh();
                    refreshRemoteSyncStatus();
                },
                Dialogs::showError);
    }

    @FXML
    private void onRestore() {
        log.info("User triggered Restore");
        runRestore(false, this::afterRestore);
    }

    private void runRestore(boolean force, java.util.function.Consumer<RestoreLocalFilesResult> onDone) {
        BackgroundTasks.run(
                () -> new RestoreLocalFilesService(
                        new LoadMachineIdentityService(ctx.keyStorePort()),
                        new DecryptFileService(ctx.encryptionPort()),
                        ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort())
                        .restore(new RestoreLocalFilesCommand(force)),
                onDone,
                Dialogs::showError);
    }

    private void afterRestore(RestoreLocalFilesResult result) {
        Dialogs.showInfo(Messages.get("mainWindow.restore.title"),
                Messages.get("mainWindow.restore.summary", result.restoredPaths().size(), result.unauthorizedPaths().size()));
        if (!result.restoredPaths().isEmpty()) {
            refresh();
        }
        if (!result.skippedPaths().isEmpty()
                && Dialogs.confirm(Messages.get("mainWindow.restore.forceTitle"),
                        Messages.get("mainWindow.restore.forceConfirm", result.skippedPaths().size()))) {
            runRestore(true, forced -> {
                Dialogs.showInfo(Messages.get("mainWindow.restore.title"),
                        Messages.get("mainWindow.restore.summary", forced.restoredPaths().size(), forced.unauthorizedPaths().size()));
                refresh();
            });
        }
    }

    @FXML
    private void onClearLocal() {
        if (!Dialogs.confirm(Messages.get("mainWindow.clearLocal.confirmTitle"), Messages.get("mainWindow.clearLocal.confirmBody"))) {
            return;
        }
        log.info("User triggered Clear Local");
        runClearLocal(false, this::afterClearLocal);
    }

    private void runClearLocal(boolean includeUnpublished, java.util.function.Consumer<ClearLocalFilesResult> onDone) {
        BackgroundTasks.run(
                () -> new ClearLocalFilesService(
                        new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()), ctx.localFiles())
                        .clear(new ClearLocalFilesCommand(includeUnpublished)),
                onDone,
                Dialogs::showError);
    }

    private void afterClearLocal(ClearLocalFilesResult result) {
        Dialogs.showInfo(Messages.get("mainWindow.clearLocal.title"),
                Messages.get("mainWindow.clearLocal.summary", result.clearedPaths().size()));
        if (!result.clearedPaths().isEmpty()) {
            refresh();
        }
        if (!result.unpublishedPaths().isEmpty()
                && Dialogs.confirm(Messages.get("mainWindow.clearLocal.forceTitle"),
                        Messages.get("mainWindow.clearLocal.forceConfirm",
                                result.unpublishedPaths().size(), String.join("\n", result.unpublishedPaths())))) {
            runClearLocal(true, forced -> {
                Dialogs.showInfo(Messages.get("mainWindow.clearLocal.title"),
                        Messages.get("mainWindow.clearLocal.summary", forced.clearedPaths().size()));
                refresh();
            });
        }
    }

    /**
     * Best-effort, on-demand "is local in sync with the remote" indicator - never wired into a
     * blocking flow (that was the exact bug this replaced). A fetch failure (broken network/auth)
     * just shows "desconhecido", never an error dialog.
     */
    private void refreshRemoteSyncStatus() {
        BackgroundTasks.run(
                () -> ctx.gitRepositoryPort().remoteSyncStatus(),
                this::showRemoteSyncStatus,
                error -> showRemoteSyncStatus(null));
    }

    private void showRemoteSyncStatus(RemoteSyncStatus status) {
        remoteSyncLabel.getStyleClass().removeAll("status-synced", "status-added", "status-modified", "status-deleted");
        if (status == null) {
            remoteSyncLabel.setText(Messages.get("mainWindow.remoteSync.unknown"));
        } else if (!status.hasRemote()) {
            remoteSyncLabel.setText(Messages.get("mainWindow.remoteSync.none"));
        } else if (status.isSynced()) {
            remoteSyncLabel.setText(Messages.get("mainWindow.remoteSync.synced"));
            remoteSyncLabel.getStyleClass().add("status-synced");
        } else if (status.isDiverged()) {
            remoteSyncLabel.setText(Messages.get("mainWindow.remoteSync.diverged", status.aheadCount(), status.behindCount()));
            remoteSyncLabel.getStyleClass().add("status-deleted");
        } else if (status.aheadCount() > 0) {
            remoteSyncLabel.setText(Messages.get("mainWindow.remoteSync.ahead", status.aheadCount()));
            remoteSyncLabel.getStyleClass().add("status-added");
        } else {
            remoteSyncLabel.setText(Messages.get("mainWindow.remoteSync.behind", status.behindCount()));
            remoteSyncLabel.getStyleClass().add("status-modified");
        }
    }

    @FXML
    private void onNavFiles() {
        switchView(MainView.FILES);
    }

    @FXML
    private void onNavSettings() {
        switchView(MainView.SETTINGS);
    }

    @FXML
    private void onNavManageAccess() {
        switchView(MainView.MANAGE_ACCESS);
    }

    /**
     * Swaps the whole center area between the file browser/preview and the embedded Settings /
     * Gerir Acesso screens - the rail on the left is the persistent navigation, not the content
     * itself, so switching never loses the file tree's selection/edit state (filesView is kept as
     * a live Node, just detached and reattached rather than rebuilt).
     */
    private void switchView(MainView view) {
        if (currentView == view) {
            return;
        }
        Parent content = switch (view) {
            case FILES -> filesView;
            case SETTINGS -> capWidth(SettingsController.embed(onLanguageChanged, () -> switchView(MainView.FILES)));
            case MANAGE_ACCESS -> capWidth(ManageAccessController.embed(ctx, this::refresh, null));
        };
        centerContainer.getChildren().setAll(content);
        currentView = view;
        syncRailSelection(view);
    }

    /**
     * Keeps the rail's highlighted item in sync even when switchView is called from code rather
     * than from a rail button click (e.g. Settings' "Fechar" navigating back to Files) - a
     * ToggleGroup only updates its selection on its own when a ToggleButton is clicked directly.
     */
    private void syncRailSelection(MainView view) {
        ToggleButton button = switch (view) {
            case FILES -> filesNavButton;
            case SETTINGS -> settingsNavButton;
            case MANAGE_ACCESS -> manageAccessNavButton;
        };
        if (!button.isSelected()) {
            button.setSelected(true);
        }
    }

    private static Parent capWidth(Parent content) {
        if (content instanceof Region region) {
            region.setMaxWidth(760);
        }
        return content;
    }

    @FXML
    private void onToggleRail() {
        railExpanded = !railExpanded;
        navRail.getStyleClass().removeAll("nav-rail-expanded", "nav-rail-collapsed");
        navRail.getStyleClass().add(railExpanded ? "nav-rail-expanded" : "nav-rail-collapsed");
        filesNavButton.setText(Messages.get(railExpanded ? "mainWindow.nav.files.expanded" : "mainWindow.nav.files.collapsed"));
        settingsNavButton.setText(Messages.get(railExpanded ? "mainWindow.nav.settings.expanded" : "mainWindow.nav.settings.collapsed"));
        manageAccessNavButton.setText(Messages.get(railExpanded ? "mainWindow.nav.manageAccess.expanded" : "mainWindow.nav.manageAccess.collapsed"));
    }

    @FXML
    private void onPublish() {
        log.info("User triggered Publish");
        // The local scan itself never touches the network, so this first step can never fail
        // because of a broken remote/credentials. Whether to publish to the remote is asked
        // separately, inside ChangeReviewController, only when there's something new to encrypt.
        BackgroundTasks.run(
                () -> new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()).scan(),
                (List<VaultChange> changes) -> {
                    if (changes.isEmpty()) {
                        offerPushWhenNothingToCommit();
                        return;
                    }
                    // vaultPathLabel lives in the top toolbar, which (unlike fileTable) is never
                    // detached from the scene when the rail switches away from the Files view.
                    Stage owner = (Stage) vaultPathLabel.getScene().getWindow();
                    ChangeReviewController.open(owner, ctx, changes, this::refreshAfterPublish);
                },
                Dialogs::showError);
    }

    /**
     * Nothing new to encrypt doesn't mean there's nothing to push - a previous publish may have
     * committed locally but never reached the remote (push declined or failed). Checking this
     * means a fetch, so it's best-effort: any failure degrades to the plain "nothing to publish"
     * message instead of an error dialog.
     */
    private void offerPushWhenNothingToCommit() {
        BackgroundTasks.run(
                () -> ctx.gitRepositoryPort().hasRemote() ? ctx.gitRepositoryPort().remoteSyncStatus() : RemoteSyncStatus.noRemote(),
                status -> {
                    if (!status.hasRemote()) {
                        Dialogs.showInfo(Messages.get("mainWindow.publish.title"), Messages.get("mainWindow.publish.nothing"));
                    } else if (status.isDiverged()) {
                        Dialogs.showInfo(Messages.get("mainWindow.publish.title"),
                                Messages.get("mainWindow.publish.diverged", status.aheadCount(), status.behindCount()));
                    } else if (status.aheadCount() > 0) {
                        if (Dialogs.confirm(Messages.get("mainWindow.publish.remoteTitle"),
                                Messages.get("mainWindow.publish.aheadConfirm", status.aheadCount()))) {
                            RemotePush.pushInBackground(ctx.gitRepositoryPort(), this::refreshRemoteSyncStatus);
                        }
                    } else if (status.behindCount() > 0) {
                        Dialogs.showInfo(Messages.get("mainWindow.publish.title"), Messages.get("mainWindow.publish.behind"));
                    } else {
                        Dialogs.showInfo(Messages.get("mainWindow.publish.title"), Messages.get("mainWindow.publish.nothing"));
                    }
                },
                error -> Dialogs.showInfo(Messages.get("mainWindow.publish.title"), Messages.get("mainWindow.publish.unknownRemote")));
    }

    private void refreshAfterPublish() {
        refresh();
        refreshRemoteSyncStatus();
    }

    private void refresh() {
        refresh(true);
    }

    private void refresh(boolean reportErrors) {
        String previouslySelectedPath = currentSelectedPath();
        BackgroundTasks.run(
                () -> {
                    List<String> localPaths = ctx.localFiles().listFiles();
                    List<VaultChange> changes = new ScanChangesService(
                            ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()).scan();
                    List<FileEntry> flat = FileTreeBuilder.build(localPaths, changes);
                    return FolderTreeBuilder.build(flat);
                },
                root -> {
                    TreeItem<FolderTreeNode> rootItem = toTreeItem(root);
                    fileTable.setRoot(rootItem);
                    if (previouslySelectedPath != null) {
                        reselect(rootItem, previouslySelectedPath);
                    }
                    boolean empty = rootItem.getChildren().isEmpty();
                    fileTable.setVisible(!empty);
                    fileTable.setManaged(!empty);
                    emptyStateLabel.setVisible(empty);
                    emptyStateLabel.setManaged(empty);
                },
                error -> {
                    if (reportErrors) {
                        Dialogs.showError(error);
                    }
                });
    }

    private String currentSelectedPath() {
        TreeItem<FolderTreeNode> selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue().fileEntry() == null) {
            return null;
        }
        return selected.getValue().fileEntry().path();
    }

    private boolean reselect(TreeItem<FolderTreeNode> item, String path) {
        FileEntry entry = item.getValue().fileEntry();
        if (entry != null && entry.path().equals(path)) {
            fileTable.getSelectionModel().select(item);
            return true;
        }
        for (TreeItem<FolderTreeNode> child : item.getChildren()) {
            if (reselect(child, path)) {
                return true;
            }
        }
        return false;
    }

    private static TreeItem<FolderTreeNode> toTreeItem(FolderTreeNode node) {
        TreeItem<FolderTreeNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        for (FolderTreeNode child : node.children()) {
            item.getChildren().add(toTreeItem(child));
        }
        return item;
    }
}
