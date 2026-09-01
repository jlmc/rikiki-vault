package io.github.jlmc.rikikivault.core.ports.out;

import java.util.List;

public interface FileStoragePort {

    List<String> listFiles();

    byte[] readFile(String relativePath);
}
