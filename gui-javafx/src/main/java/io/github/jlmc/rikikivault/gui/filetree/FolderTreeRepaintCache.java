package io.github.jlmc.rikikivault.gui.filetree;

import java.util.Objects;

/**
 * Remembers what is currently painted on screen for the auto-refreshed folder tree and its file
 * preview, so a repeated auto-refresh tick that found no real change never touches the
 * TreeTableView/preview Nodes - that unconditional touch is what causes the UI to flicker. Holds
 * no JavaFX state; the controller is responsible for applying the repaint decision to its Nodes.
 */
public final class FolderTreeRepaintCache {

    private FolderTreeNode renderedRoot;
    private FolderTreeNode renderedPreviewNode;
    private boolean previewRendered;

    /** True when {@code root} differs from the tree currently on screen; remembers it either way. */
    public boolean rootChanged(FolderTreeNode root) {
        if (Objects.equals(root, renderedRoot)) {
            return false;
        }
        renderedRoot = root;
        return true;
    }

    /**
     * True when {@code node} differs from what the preview panel currently shows (or nothing has
     * been rendered yet / the cache was invalidated); remembers {@code node} as the new baseline.
     */
    public boolean previewNeedsRepaint(FolderTreeNode node) {
        if (previewRendered && Objects.equals(node, renderedPreviewNode)) {
            return false;
        }
        renderedPreviewNode = node;
        previewRendered = true;
        return true;
    }

    /**
     * Forces the next previewNeedsRepaint() call to return true regardless of the node passed in
     * - used when the preview panel's content was replaced by something else (the editor) without
     * changing which file is selected.
     */
    public void invalidatePreview() {
        previewRendered = false;
    }
}
