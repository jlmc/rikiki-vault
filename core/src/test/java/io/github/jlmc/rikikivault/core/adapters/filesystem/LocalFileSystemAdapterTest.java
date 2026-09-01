package io.github.jlmc.rikikivault.core.adapters.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileSystemAdapterTest {

    @Test
    void emptyRootReturnsEmptyList(@TempDir Path tempDir) {
        Path root = tempDir.resolve("local");
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertTrue(adapter.listFiles().isEmpty());
    }

    @Test
    void rootWithOnlySubdirectoriesReturnsEmptyList(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("local");
        Files.createDirectories(root.resolve("empty-subdir"));
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertTrue(adapter.listFiles().isEmpty());
    }

    @Test
    void listsNestedFilesWithForwardSlashRelativePaths(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("local");
        Files.createDirectories(root.resolve("cv"));
        Files.createDirectories(root.resolve("contracts").resolve("old"));
        Files.writeString(root.resolve("cv").resolve("CV.pdf"), "cv content");
        Files.writeString(root.resolve("contracts").resolve("old").resolve("contract.pdf"), "contract content");
        Files.writeString(root.resolve("notes.md"), "notes content");

        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertEquals(
                List.of("contracts/old/contract.pdf", "cv/CV.pdf", "notes.md"),
                adapter.listFiles());
    }

    @Test
    void readFileReturnsExactBytes(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("local");
        Files.createDirectories(root);
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("notes.md"), content);

        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertArrayEquals(content, adapter.readFile("notes.md"));
    }

    @Test
    void unicodeFileNameRoundTrips(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("local");
        Files.createDirectories(root.resolve("café"));
        byte[] content = "café content".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("café").resolve("relatório.pdf"), content);

        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertEquals(List.of("café/relatório.pdf"), adapter.listFiles());
        assertArrayEquals(content, adapter.readFile("café/relatório.pdf"));
    }

    @Test
    void readFileRejectsAPathThatEscapesTheRootEvenWhenTheTargetExists(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("local");
        Files.createDirectories(root);
        Path outsideSecret = tempDir.resolve("secret.txt");
        Files.writeString(outsideSecret, "should never be readable via the adapter");

        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertThrows(IllegalArgumentException.class, () -> adapter.readFile("../secret.txt"));
    }
}
