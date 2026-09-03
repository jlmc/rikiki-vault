package io.github.jlmc.rikikivault.gui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TextFileViewerTest {

    private final TextFileViewer viewer = new TextFileViewer();

    @Test
    void alwaysSupports() {
        assertEquals(true, viewer.supports("whatever.bin"));
    }

    @Test
    void decodesUtf8Content() {
        ViewerResult result = viewer.view("hello, café".getBytes(StandardCharsets.UTF_8), "notes.txt");

        ViewerResult.TextViewerResult text = assertInstanceOf(ViewerResult.TextViewerResult.class, result);
        assertEquals("hello, café", text.text());
    }

    @Test
    void reportsUnsupportedForNonUtf8Bytes() {
        byte[] invalidUtf8 = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x01};

        ViewerResult result = viewer.view(invalidUtf8, "data.bin");

        assertInstanceOf(ViewerResult.UnsupportedViewerResult.class, result);
    }
}
