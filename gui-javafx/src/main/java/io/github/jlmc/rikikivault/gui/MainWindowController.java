package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.diff.TextDiffAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.DiffFileService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PullVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.RevertFileService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.DiffFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevertFileCommand;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MainWindowController {

    @FXML private Label vaultPathLabel;
    @FXML private Label fingerprintLabel;
    @FXML private TreeTableView<FolderTreeNode> fileTable;
    @FXML private TreeTableColumn<FolderTreeNode, String> nameColumn;
    @FXML private TreeTableColumn<FolderTreeNode, FolderTreeNode> statusColumn;
    @FXML private StackPane previewContainer;
    @FXML private Button editToggleButton;
    @FXML private Button saveButton;
    @FXML private Button encryptButton;
    @FXML private Button revertButton;
    @FXML private Button diffButton;

    private VaultContext ctx;
    private FolderTreeNode currentNode;
    private boolean editMode;
    private TextArea editorArea;

    void init(VaultContext ctx) {
        this.ctx = ctx;
        MachineIdentity identity = new LoadMachineIdentityService(ctx.keyStorePort()).load();
        vaultPathLabel.setText(ctx.vaultRoot().toString());
        fingerprintLabel.setText("Identidade: " + identity.id());

        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getValue().name()));
        statusColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getValue()));
        statusColumn.setCellFactory(column -> new StatusBadgeTreeCell<>(
                node -> node.fileEntry() == null ? null : node.fileEntry().status()));

        fileTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> showPreview(newValue == null ? null : newValue.getValue()));
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

    private void showPreview(FolderTreeNode node) {
        String newPath = node != null && !node.isFolder() ? node.fileEntry().path() : null;
        String oldPath = currentNode != null && !currentNode.isFolder() ? currentNode.fileEntry().path() : null;
        boolean sameFileReselectedWhileEditing = editMode && newPath != null && newPath.equals(oldPath);
        currentNode = node;
        if (sameFileReselectedWhileEditing) {
            return;
        }
        exitEditMode();

        if (node == null) {
            editToggleButton.setDisable(true);
            previewContainer.getChildren().setAll(ViewerResultRenderer.renderUnsupported("Seleciona um ficheiro para pré-visualizar."));
            return;
        }
        if (node.isFolder()) {
            editToggleButton.setDisable(true);
            previewContainer.getChildren().setAll(ViewerResultRenderer.renderUnsupported("Selecionaste uma pasta."));
            return;
        }
        FileEntry entry = node.fileEntry();
        if (entry.status() == FileStatus.DELETED) {
            editToggleButton.setDisable(true);
            previewContainer.getChildren().setAll(
                    ViewerResultRenderer.renderUnsupported("Ficheiro removido - sem conteúdo local para pré-visualizar."));
            return;
        }
        BackgroundTask.run(
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
                            ViewerResultRenderer.renderUnsupported("Não foi possível pré-visualizar: " + error.getMessage()));
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
        BackgroundTask.run(
                () -> ctx.localFiles().readFile(path),
                bytes -> {
                    editorArea = new TextArea(TextFileViewer.tryDecodeUtf8(bytes));
                    editorArea.setWrapText(false);
                    editorArea.getStyleClass().add("preview-text");
                    previewContainer.getChildren().setAll(editorArea);
                    editMode = true;
                    editToggleButton.setText("Ver");
                    setEditActionButtonsVisible(true);
                },
                Dialogs::showError);
    }

    private void exitEditMode() {
        editMode = false;
        editorArea = null;
        editToggleButton.setText("Editar");
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
        BackgroundTask.runVoid(
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
        BackgroundTask.runVoid(
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
        if (!Dialogs.confirm("Revert", "Descartar as alterações locais de " + path + " e voltar à última versão publicada?")) {
            return;
        }
        BackgroundTask.runVoid(
                () -> new RevertFileService(
                        ctx.manifestPort(), ctx.localFiles(), ctx.documentsFiles(),
                        new DecryptFileService(ctx.encryptionPort()), new LoadMachineIdentityService(ctx.keyStorePort()))
                        .revert(new RevertFileCommand(path)),
                () -> reloadEditorContent(path),
                Dialogs::showError);
    }

    private void reloadEditorContent(String path) {
        BackgroundTask.run(
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
        BackgroundTask.run(
                () -> new DiffFileService(
                        ctx.manifestPort(), ctx.documentsFiles(),
                        new DecryptFileService(ctx.encryptionPort()), new LoadMachineIdentityService(ctx.keyStorePort()),
                        new TextDiffAdapter())
                        .diff(new DiffFileCommand(path, currentContent)),
                diffText -> {
                    Stage owner = (Stage) fileTable.getScene().getWindow();
                    DiffResultController.open(owner, diffText);
                },
                Dialogs::showError);
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
        String previouslySelectedPath = currentSelectedPath();
        BackgroundTask.run(
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
