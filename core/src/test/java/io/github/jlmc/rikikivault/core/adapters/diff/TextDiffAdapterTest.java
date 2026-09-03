package io.github.jlmc.rikikivault.core.adapters.diff;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDiffAdapterTest {

    private final TextDiffAdapter adapter = new TextDiffAdapter();

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void identicalContentHasNoAddedOrRemovedLines() {
        byte[] content = bytes("line one\nline two\n");

        String diff = adapter.diff(content, content);

        assertTrue(diff.startsWith("--- previous\n+++ current\n"));
        List<String> bodyLines = diff.lines().skip(2).toList();
        assertTrue(bodyLines.stream().allMatch(line -> line.startsWith(" ")));
    }

    @Test
    void reportsAddedLines() {
        String diff = adapter.diff(bytes("line one\n"), bytes("line one\nline two\n"));

        assertTrue(diff.contains("+line two"));
        assertFalse(diff.contains("-line one"));
    }

    @Test
    void reportsRemovedLines() {
        String diff = adapter.diff(bytes("line one\nline two\n"), bytes("line one\n"));

        assertTrue(diff.contains("-line two"));
        assertFalse(diff.contains("+line one"));
    }

    @Test
    void reportsModifiedLinesAsRemovedPlusAdded() {
        String diff = adapter.diff(bytes("hello\n"), bytes("goodbye\n"));

        assertTrue(diff.contains("-hello"));
        assertTrue(diff.contains("+goodbye"));
    }

    @Test
    void binaryContentOnEitherSideReportsFileChanged() {
        byte[] binary = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x01};

        assertEquals("File changed", adapter.diff(binary, bytes("text")));
        assertEquals("File changed", adapter.diff(bytes("text"), binary));
    }
}
