package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTreeBuilderTest {

    @Test
    void aLocalPathWithNoMatchingChangeIsSynced() {
        List<FileEntry> entries = FileTreeBuilder.build(List.of("cv.pdf"), List.of());

        assertEquals(List.of(new FileEntry("cv.pdf", FileStatus.SYNCED)), entries);
    }

    @Test
    void aLocalPathWithAMatchingChangeUsesTheChangeStatus() {
        List<FileEntry> entries = FileTreeBuilder.build(
                List.of("cv.pdf", "notes.md"),
                List.of(new VaultChange(ChangeType.MODIFIED, "cv.pdf")));

        assertEquals(List.of(
                new FileEntry("cv.pdf", FileStatus.MODIFIED),
                new FileEntry("notes.md", FileStatus.SYNCED)), entries);
    }

    @Test
    void aChangeWithNoLocalFileIsStillListedAsDeleted() {
        List<FileEntry> entries = FileTreeBuilder.build(
                List.of(),
                List.of(new VaultChange(ChangeType.DELETED, "old.pdf")));

        assertEquals(List.of(new FileEntry("old.pdf", FileStatus.DELETED)), entries);
    }

    @Test
    void anAddedPathIsListedAsAdded() {
        List<FileEntry> entries = FileTreeBuilder.build(
                List.of("new.txt"),
                List.of(new VaultChange(ChangeType.ADDED, "new.txt")));

        assertEquals(List.of(new FileEntry("new.txt", FileStatus.ADDED)), entries);
    }

    @Test
    void resultsAreSortedByPath() {
        List<FileEntry> entries = FileTreeBuilder.build(List.of("b.txt", "a.txt", "c.txt"), List.of());

        assertEquals(List.of("a.txt", "b.txt", "c.txt"), entries.stream().map(FileEntry::path).toList());
    }
}
