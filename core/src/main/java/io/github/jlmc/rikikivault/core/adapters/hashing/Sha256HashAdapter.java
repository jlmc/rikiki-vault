package io.github.jlmc.rikikivault.core.adapters.hashing;

import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.ports.out.HashPort;

public final class Sha256HashAdapter implements HashPort {

    @Override
    public FileHash hash(byte[] content) {
        return FileHash.of(content);
    }
}
