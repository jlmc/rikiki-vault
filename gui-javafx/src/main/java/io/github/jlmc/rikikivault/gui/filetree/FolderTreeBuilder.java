package io.github.jlmc.rikikivault.gui.filetree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the flat, slash-separated list into a folder
 * hierarchy - reuses that status
 * computation as-is, only regroups it.
 */
public final class FolderTreeBuilder {

    private FolderTreeBuilder() {
    }

    public static FolderTreeNode build(List<FileEntry> flatEntries) {
        return buildLevel("", flatEntries, 0);
    }

    private static FolderTreeNode buildLevel(String name, List<FileEntry> entries, int depth) {
        Map<String, List<FileEntry>> subfolders = new LinkedHashMap<>();
        List<FileEntry> filesHere = new ArrayList<>();

        for (FileEntry entry : entries) {
            String[] segments = entry.path().split("/");
            if (depth == segments.length - 1) {
                filesHere.add(entry);
            } else {
                subfolders.computeIfAbsent(segments[depth], _ -> new ArrayList<>()).add(entry);
            }
        }

        List<FolderTreeNode> children = new ArrayList<>();
        subfolders.forEach((folderName, folderEntries) -> children.add(buildLevel(folderName, folderEntries, depth + 1)));
        children.sort(Comparator.comparing(FolderTreeNode::name));

        List<FolderTreeNode> fileNodes = filesHere.stream()
                .map(entry -> new FolderTreeNode(lastSegment(entry.path()), entry, List.of()))
                .sorted(Comparator.comparing(FolderTreeNode::name))
                .toList();

        List<FolderTreeNode> all = new ArrayList<>(children);
        all.addAll(fileNodes);
        return new FolderTreeNode(name, null, all);
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
