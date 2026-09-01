package io.github.jlmc.rikikivault.core.adapters.filesystem;

import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Scans a single root directory (e.g. {@code local/}). Bound to one root per instance, mirroring
 * {@link io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter}'s
 * constructor-injected-path style.
 */
public final class LocalFileSystemAdapter implements FileStoragePort {

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
                    .map(this::toRelativeSlashPath)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list files under " + rootDirectory, e);
        }
    }

    @Override
    public byte[] readFile(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Path resolved = rootDirectory.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("relativePath escapes the root directory: " + relativePath);
        }
        try {
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file at " + resolved, e);
        }
    }

    private String toRelativeSlashPath(Path file) {
        Path relative = rootDirectory.relativize(file);
        return StreamSupport.stream(relative.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("/"));
    }
}
