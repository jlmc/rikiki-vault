package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.model.FileHash;

public interface HashPort {

    FileHash hash(byte[] content);
}
