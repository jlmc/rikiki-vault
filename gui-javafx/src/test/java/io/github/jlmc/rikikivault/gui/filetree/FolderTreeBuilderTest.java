package io.github.jlmc.rikikivault.gui.filetree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderTreeBuilderTest {

    @Test
    void emptyListProducesAnEmptyRoot() {
        FolderTreeNode root = FolderTreeBuilder.build(List.of());

        assertTrue(root.isFolder());
        assertTrue(root.children().isEmpty());
    }

    @Test
    void filesAtTheTopLevelBecomeDirectChildrenOfTheRoot() {
        List<FileEntry> entries = List.of(
                new FileEntry("notes.md", FileStatus.SYNCED),
                new FileEntry("cv.pdf", FileStatus.MODIFIED));

        FolderTreeNode root = FolderTreeBuilder.build(entries);

        assertEquals(List.of("cv.pdf", "notes.md"), root.children().stream().map(FolderTreeNode::name).toList());
        assertTrue(root.children().stream().noneMatch(FolderTreeNode::isFolder));
    }

    @Test
    void nestedPathsBecomeFolderNodes() {
        List<FileEntry> entries = List.of(
                new FileEntry("cv/CV.pdf", FileStatus.SYNCED),
                new FileEntry("cv/old/CV-2020.pdf", FileStatus.MODIFIED));

        FolderTreeNode root = FolderTreeBuilder.build(entries);

        assertEquals(1, root.children().size());
        FolderTreeNode cvFolder = root.children().get(0);
        assertEquals("cv", cvFolder.name());
        assertTrue(cvFolder.isFolder());
        assertEquals(List.of("old", "CV.pdf"), cvFolder.children().stream().map(FolderTreeNode::name).toList());

        FolderTreeNode oldFolder = cvFolder.children().get(0);
        assertTrue(oldFolder.isFolder());
        assertEquals(List.of("CV-2020.pdf"), oldFolder.children().stream().map(FolderTreeNode::name).toList());
    }

    @Test
    void foldersAreOrderedBeforeFilesAtTheSameLevel() {
        List<FileEntry> entries = List.of(
                new FileEntry("z-file.txt", FileStatus.SYNCED),
                new FileEntry("a-folder/inner.txt", FileStatus.SYNCED));

        FolderTreeNode root = FolderTreeBuilder.build(entries);

        assertEquals(List.of("a-folder", "z-file.txt"), root.children().stream().map(FolderTreeNode::name).toList());
        assertTrue(root.children().get(0).isFolder());
        assertTrue(!root.children().get(1).isFolder());
    }

    /**
     * MainWindowController's auto-refresh relies on FolderTreeNode/FileEntry's record equality to
     * decide "did anything actually change?" before touching the UI - this fixes that contract.
     */
    @Test
    void identicalFlatInputsProduceEqualTrees() {
        List<FileEntry> entries = List.of(
                new FileEntry("cv/CV.pdf", FileStatus.SYNCED),
                new FileEntry("notes.md", FileStatus.MODIFIED));
        List<FileEntry> reordered = List.of(
                new FileEntry("notes.md", FileStatus.MODIFIED),
                new FileEntry("cv/CV.pdf", FileStatus.SYNCED));

        assertEquals(FolderTreeBuilder.build(entries), FolderTreeBuilder.build(reordered));
    }

    @Test
    void aDifferentStatusForTheSamePathProducesUnequalTrees() {
        FolderTreeNode synced = FolderTreeBuilder.build(List.of(new FileEntry("notes.md", FileStatus.SYNCED)));
        FolderTreeNode modified = FolderTreeBuilder.build(List.of(new FileEntry("notes.md", FileStatus.MODIFIED)));

        assertNotEquals(synced, modified);
    }
}
