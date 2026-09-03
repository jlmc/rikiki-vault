package io.github.jlmc.rikikivault.core.adapters.filesystem;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

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
    void ignoresKnownOsJunkFiles(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("local");
        Files.createDirectories(root.resolve("cv"));
        Files.writeString(root.resolve(".DS_Store"), "junk");
        Files.writeString(root.resolve("cv").resolve(".DS_Store"), "junk");
        Files.writeString(root.resolve("Thumbs.db"), "junk");
        Files.writeString(root.resolve("cv").resolve("CV.pdf"), "cv content");

        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertEquals(List.of("cv/CV.pdf"), adapter.listFiles());
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

    @Test
    void writeFileCreatesNestedDirectoriesAndIsReadableBack(@TempDir Path tempDir) {
        Path root = tempDir.resolve("documents");
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);
        byte[] content = "cv content".getBytes(StandardCharsets.UTF_8);

        adapter.writeFile("cv/cv.pdf.enc", content);

        assertArrayEquals(content, adapter.readFile("cv/cv.pdf.enc"));
    }

    @Test
    void writeFileOverwritesExistingContent(@TempDir Path tempDir) {
        Path root = tempDir.resolve("documents");
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        adapter.writeFile("notes.md.enc", "old".getBytes(StandardCharsets.UTF_8));
        adapter.writeFile("notes.md.enc", "new".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), adapter.readFile("notes.md.enc"));
    }

    @Test
    void writeFileProducesAnOwnerOnlyFileOnPosixFilesystems(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported on this filesystem");
        Path root = tempDir.resolve("local");
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        adapter.writeFile("notes.md", "content".getBytes(StandardCharsets.UTF_8));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(root.resolve("notes.md"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions);
    }

    @Test
    void writeFileLeavesNoTempFileBehind(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("local");
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        adapter.writeFile("notes.md", "first".getBytes(StandardCharsets.UTF_8));
        adapter.writeFile("notes.md", "second".getBytes(StandardCharsets.UTF_8));

        try (var entries = Files.list(root)) {
            List<String> fileNames = entries.map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of("notes.md"), fileNames);
        }
    }

    @Test
    void deleteFileRemovesAnExistingFile(@TempDir Path tempDir) {
        Path root = tempDir.resolve("documents");
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);
        adapter.writeFile("notes.md.enc", "content".getBytes(StandardCharsets.UTF_8));

        adapter.deleteFile("notes.md.enc");

        assertTrue(adapter.listFiles().isEmpty());
    }

    @Test
    void deleteFileOnAMissingPathIsASafeNoOp(@TempDir Path tempDir) {
        Path root = tempDir.resolve("documents");
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        adapter.deleteFile("never-existed.txt");
    }

    @Test
    void writeFileRejectsAPathThatEscapesTheRoot(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("documents");
        Files.createDirectories(root);
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertThrows(IllegalArgumentException.class,
                () -> adapter.writeFile("../escape.txt", "x".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deleteFileRejectsAPathThatEscapesTheRoot(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("documents");
        Files.createDirectories(root);
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(root);

        assertThrows(IllegalArgumentException.class, () -> adapter.deleteFile("../escape.txt"));
    }
}
