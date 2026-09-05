package io.github.jlmc.rikikivault.core.adapters.filesystem;

import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Scans a single root directory (e.g. {@code local/}). Bound to one root per instance, mirroring
 * {@link io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter}'s
 * constructor-injected-path style.
 */
public final class LocalFileSystemAdapter implements FileStoragePort {

    private static final Set<String> IGNORED_FILE_NAMES = Set.of(".DS_Store", "Thumbs.db", "desktop.ini");

    private final Path rootDirectory;

    public LocalFileSystemAdapter(Path rootDirectory) {
        Objects.requireNonNull(rootDirectory, "rootDirectory must not be null");
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    }

    @Override
    public List<String> listFiles() {
        if (!Files.exists(rootDirectory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(rootDirectory)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> !IGNORED_FILE_NAMES.contains(file.getFileName().toString()))
                    .map(this::toRelativeSlashPath)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list files under " + rootDirectory, e);
        }
    }

    @Override
    public byte[] readFile(String relativePath) {
        Path resolved = resolveWithinRoot(relativePath);
        try {
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file at " + resolved, e);
        }
    }

    @Override
    public void writeFile(String relativePath, byte[] content) {
        // Written content may be decrypted plaintext: write to a sibling temp file,
        // restrict its permissions, then atomically move it into place - a crash mid-write can
        // never leave a truncated/partial file at the final path, and it's never briefly
        // world-readable.
        Objects.requireNonNull(content, "content must not be null");
        Path resolved = resolveWithinRoot(relativePath);
        try {
            Path parent = resolved.getParent();
            if (parent != null) {
                createDirectoriesSecurely(parent);
            }
            Path tempFile = Files.createTempFile(parent, resolved.getFileName().toString(), ".tmp");
            try {
                Files.write(tempFile, content, StandardOpenOption.TRUNCATE_EXISTING);
                restrictToOwnerOnly(tempFile);
                moveIntoPlace(tempFile, resolved);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file at " + resolved, e);
        }
    }

    @Override
    public void deleteFile(String relativePath) {
        Path resolved = resolveWithinRoot(relativePath);
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file at " + resolved, e);
        }
    }

    private static void moveIntoPlace(Path tempFile, Path destination) throws IOException {
        try {
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems (e.g. certain network mounts) can't rename atomically - falling
            // back still avoids ever writing a truncated file directly at the destination.
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void createDirectoriesSecurely(Path directory) throws IOException {
        // Only restricts newly created directories - one already sitting there from before this
        // method existed keeps whatever permissions it already had (no retroactive migration).
        if (Files.exists(directory)) {
            return;
        }
        if (supportsPosixPermissions(directory)) {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(ownerOnly));
        } else {
            // Non-POSIX filesystem (e.g. Windows): no atomic "create with permissions" available.
            Files.createDirectories(directory);
            java.io.File asFile = directory.toFile();
            asFile.setReadable(false, false);
            asFile.setReadable(true, true);
            asFile.setWritable(false, false);
            asFile.setWritable(true, true);
            asFile.setExecutable(false, false);
            asFile.setExecutable(true, true);
        }
    }

    private static boolean supportsPosixPermissions(Path path) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }

    private static void restrictToOwnerOnly(Path file) throws IOException {
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            Set<PosixFilePermission> ownerReadWrite = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, ownerReadWrite);
        } else {
            // Non-POSIX filesystem (e.g. Windows): best-effort fallback, not a full ACL solution.
            java.io.File asFile = file.toFile();
            asFile.setReadable(false, false);
            asFile.setReadable(true, true);
            asFile.setWritable(false, false);
            asFile.setWritable(true, true);
        }
    }

    private Path resolveWithinRoot(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Path resolved = rootDirectory.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("relativePath escapes the root directory: " + relativePath);
        }
        return resolved;
    }

    private String toRelativeSlashPath(Path file) {
        Path relative = rootDirectory.relativize(file);
        return StreamSupport.stream(relative.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("/"));
    }
}
