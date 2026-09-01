package io.github.jlmc.rikikivault.core.ports.out;

import java.util.List;

public interface FileStoragePort {

    List<String> listFiles();

    byte[] readFile(String relativePath);

    void writeFile(String relativePath, byte[] content);

    void deleteFile(String relativePath);
}
