package io.github.jlmc.rikikivault.gui.filetree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderTreeRepaintCacheTest {

    @Test
    void firstRootIsAlwaysTreatedAsChanged() {
        FolderTreeRepaintCache cache = new FolderTreeRepaintCache();
        FolderTreeNode root = FolderTreeBuilder.build(List.of(new FileEntry("notes.md", FileStatus.SYNCED)));

        assertTrue(cache.rootChanged(root));
    }

    @Test
    void anEqualRootIsNotTreatedAsChangedAgain() {
        FolderTreeRepaintCache cache = new FolderTreeRepaintCache();
        List<FileEntry> entries = List.of(new FileEntry("notes.md", FileStatus.SYNCED));
        cache.rootChanged(FolderTreeBuilder.build(entries));

        boolean changed = cache.rootChanged(FolderTreeBuilder.build(entries));

        assertFalse(changed);
    }

    @Test
    void aDifferentRootIsTreatedAsChanged() {
        FolderTreeRepaintCache cache = new FolderTreeRepaintCache();
        cache.rootChanged(FolderTreeBuilder.build(List.of(new FileEntry("notes.md", FileStatus.SYNCED))));

        boolean changed = cache.rootChanged(FolderTreeBuilder.build(List.of(new FileEntry("notes.md", FileStatus.MODIFIED))));

        assertTrue(changed);
    }

    @Test
    void firstPreviewIsAlwaysTreatedAsNeedingRepaint() {
        FolderTreeRepaintCache cache = new FolderTreeRepaintCache();

        assertTrue(cache.previewNeedsRepaint(null));
    }

    @Test
    void anEqualNodeDoesNotNeedRepaintAgain() {
        FolderTreeRepaintCache cache = new FolderTreeRepaintCache();
        FolderTreeNode node = new FolderTreeNode("notes.md", new FileEntry("notes.md", FileStatus.SYNCED), List.of());
        cache.previewNeedsRepaint(node);

        boolean needsRepaint = cache.previewNeedsRepaint(
                new FolderTreeNode("notes.md", new FileEntry("notes.md", FileStatus.SYNCED), List.of()));

        assertFalse(needsRepaint);
    }

    @Test
    void aDifferentNodeNeedsRepaint() {
        FolderTreeRepaintCache cache = new FolderTreeRepaintCache();
        cache.previewNeedsRepaint(new FolderTreeNode("notes.md", new FileEntry("notes.md", FileStatus.SYNCED), List.of()));

        boolean needsRepaint = cache.previewNeedsRepaint(
                new FolderTreeNode("notes.md", new FileEntry("notes.md", FileStatus.MODIFIED), List.of()));

        assertTrue(needsRepaint);
    }

    @Test
    void invalidatePreviewForcesTheNextCallToRepaintEvenForTheSameNode() {
        FolderTreeRepaintCache cache = new FolderTreeRepaintCache();
        FolderTreeNode node = new FolderTreeNode("notes.md", new FileEntry("notes.md", FileStatus.SYNCED), List.of());
        cache.previewNeedsRepaint(node);

        cache.invalidatePreview();

        assertTrue(cache.previewNeedsRepaint(node));
    }
}
