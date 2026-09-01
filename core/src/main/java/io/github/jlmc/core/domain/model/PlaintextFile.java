package io.github.jlmc.core.domain.model;

import java.util.Arrays;
import java.util.Objects;

public record PlaintextFile(String fileName, byte[] content) {

    public PlaintextFile {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaintextFile other)) return false;
        return fileName.equals(other.fileName) && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "PlaintextFile[fileName=" + fileName + ", contentLength=" + content.length + "]";
    }
}
