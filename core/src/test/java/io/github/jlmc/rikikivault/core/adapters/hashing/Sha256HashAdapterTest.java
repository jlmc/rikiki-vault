package io.github.jlmc.rikikivault.core.adapters.hashing;

import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha256HashAdapterTest {

    private final Sha256HashAdapter adapter = new Sha256HashAdapter();

    @Test
    void hashesEmptyInputToTheKnownSha256Constant() {
        FileHash hash = adapter.hash(new byte[0]);

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash.hex());
    }

    @Test
    void hashesAbcToTheStandardNistTestVector() {
        FileHash hash = adapter.hash("abc".getBytes(StandardCharsets.UTF_8));

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash.hex());
    }
}
