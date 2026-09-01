package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileHashTest {

    @Test
    void sameContentProducesSameHash() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        assertEquals(FileHash.of(content), FileHash.of(content.clone()));
    }

    @Test
    void differentContentProducesDifferentHash() {
        FileHash a = FileHash.of("a".getBytes(StandardCharsets.UTF_8));
        FileHash b = FileHash.of("b".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(a, b);
    }

    @Test
    void hexMustBeExactlySha256Length() {
        assertThrows(IllegalArgumentException.class, () -> new FileHash("abc"));
    }

    @Test
    void toBytesAndOfBytesRoundTrip() {
        FileHash hash = FileHash.of("round trip".getBytes(StandardCharsets.UTF_8));

        byte[] raw = hash.toBytes();
        assertEquals(32, raw.length);
        assertEquals(hash, FileHash.ofBytes(raw));
    }

    @Test
    void ofBytesRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> FileHash.ofBytes(new byte[]{1, 2, 3}));
    }

    @Test
    void toStringIsTheHexValue() {
        FileHash hash = FileHash.of("x".getBytes(StandardCharsets.UTF_8));

        assertEquals(hash.hex(), hash.toString());
    }
}
