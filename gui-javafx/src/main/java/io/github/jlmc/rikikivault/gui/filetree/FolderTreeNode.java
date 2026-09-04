package io.github.jlmc.rikikivault.gui.filetree;

import java.util.List;

/** A row in the folder-tree browser. A folder node has {@code fileEntry == null}. */
public record FolderTreeNode(String name, FileEntry fileEntry, List<FolderTreeNode> children) {

    public boolean isFolder() {
        return fileEntry == null;
    }
}
