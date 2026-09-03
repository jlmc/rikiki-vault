package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlaintextFileTest {

    @Test
    void toStringNeverContainsFileContent() {
        String secret = "this is the decrypted secret content";
        PlaintextFile file = new PlaintextFile("notes.md", secret.getBytes(StandardCharsets.UTF_8));

        String text = file.toString();

        assertFalse(text.contains(secret), "toString must never leak decrypted file content");
        assertEquals("PlaintextFile[fileName=notes.md, contentLength=" + secret.getBytes(StandardCharsets.UTF_8).length + "]", text);
    }
}
