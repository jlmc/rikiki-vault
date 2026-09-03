package io.github.jlmc.rikikivault.gui;

import java.util.List;

/** A row in the folder-tree browser (Plan.md §13). A folder node has {@code fileEntry == null}. */
record FolderTreeNode(String name, FileEntry fileEntry, List<FolderTreeNode> children) {

    boolean isFolder() {
        return fileEntry == null;
    }
}
