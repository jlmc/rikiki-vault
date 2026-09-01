package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FakeFileStoragePort implements FileStoragePort {

    private final Map<String, byte[]> filesByPath = new LinkedHashMap<>();

    FakeFileStoragePort withFile(String path, String content) {
        filesByPath.put(path, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    @Override
    public List<String> listFiles() {
        return List.copyOf(filesByPath.keySet());
    }

    @Override
    public byte[] readFile(String relativePath) {
        byte[] content = filesByPath.get(relativePath);
        if (content == null) {
            throw new IllegalArgumentException("No such fake file: " + relativePath);
        }
        return content;
    }
}
