package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure logic (no JavaFX dependency, so it's plain-JUnit-testable): combines the current
 * {@code local/} listing with the pending changes into one status-annotated list. A path present
 * in {@code localPaths} but absent from {@code changes} is unchanged since the last publish
 * ({@link FileStatus#SYNCED}); a path only in {@code changes} (never {@code localPaths}) is one
 * the manifest still tracks but that's gone from disk ({@link FileStatus#DELETED}).
 */
final class FileTreeBuilder {

    private FileTreeBuilder() {
    }

    static List<FileEntry> build(List<String> localPaths, List<VaultChange> changes) {
        Map<String, FileStatus> statusByPath = new LinkedHashMap<>();
        for (String path : localPaths) {
            statusByPath.put(path, FileStatus.SYNCED);
        }
        for (VaultChange change : changes) {
            statusByPath.put(change.path(), FileStatus.from(change.type()));
        }
        return statusByPath.entrySet().stream()
                .map(entry -> new FileEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(FileEntry::path))
                .toList();
    }
}
